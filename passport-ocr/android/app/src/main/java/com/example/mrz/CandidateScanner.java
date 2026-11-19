package com.example.mrz;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import ai.onnxruntime.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import java.io.*;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.*;

/**
 * On-device passport reader: learned page corners -> aligned MRZ spotter -> specialist fallback.
 * The confidence gate is development-calibrated and is not a population guarantee.
 */
public final class CandidateScanner implements PageScanner {
    private static final String[] ASSETS = {"align.onnx", "spot.onnx", "det.onnx", "rec.onnx"};
    private static final String[] HASHES = {
        "950070fd4a46e25d46db7c35d0ed77612d9063f70d4543c7c504f0da6a943a89",
        "d58b6af5501295fb1c9d93a529b6725d98d4983711f2d2b3323855d155ec2105",
        "0786844728d828dc68c19b844aa87e2b29fdba3a587468f2863824d116ffc269",
        "07cef987fc1852aac12f18e1269fbaf1e2644579ca5743061d6f9cec7bef77c0"
    };
    private static final String[] SPOT_CHARS = makeSpotAlphabet();
    private static final String[] SPECIAL_CHARS = makeSpecialAlphabet();
    private static final double SPOT_MEAN_GATE = 0.994124;
    private static final double NAME_MIN_GATE = 0.839322;
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession aligner, spotter, detector, recognizer;

    public CandidateScanner(Context context) throws Exception {
        OrtSession[] sessions = new OrtSession[4];
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(2);
        try {
            for (int i = 0; i < ASSETS.length; i++) {
                byte[] bytes = readAsset(context, ASSETS[i]);
                if (!HASHES[i].equals(sha256(bytes))) throw new IOException("Pinned scan model changed: " + ASSETS[i]);
                sessions[i] = env.createSession(bytes, options);
            }
        } catch (Exception failure) {
            for (OrtSession session : sessions) if (session != null) try { session.close(); } catch (Exception ignored) {}
            options.close();
            throw failure;
        }
        options.close();
        aligner = sessions[0]; spotter = sessions[1]; detector = sessions[2]; recognizer = sessions[3];
    }

    private static byte[] readAsset(Context context,String name) throws IOException {
        try(InputStream input=context.getAssets().open(name);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[64*1024];int count;
            while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest) out.append(String.format(Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
    private static String[] makeSpotAlphabet() {
        List<String> chars = new ArrayList<>(Arrays.asList("<PAD>", "<EOS>"));
        for (char c = 'A'; c <= 'Z'; c++) chars.add(String.valueOf(c));
        for (char c = '0'; c <= '9'; c++) chars.add(String.valueOf(c));
        chars.add("<"); chars.add("&"); return chars.toArray(new String[0]);
    }
    private static String[] makeSpecialAlphabet() {
        List<String> chars = new ArrayList<>(Arrays.asList("<PAD>", "<EOS>", "<SEP>"));
        for (char c = 'A'; c <= 'Z'; c++) chars.add(String.valueOf(c));
        for (char c = '0'; c <= '9'; c++) chars.add(String.valueOf(c));
        chars.add("<"); return chars.toArray(new String[0]);
    }
    private static Point[] order(Point[] points) {
        Point[] sorted = points.clone(); Arrays.sort(sorted, Comparator.comparingDouble(p -> p.x));
        Point tl = sorted[0].y < sorted[1].y ? sorted[0] : sorted[1];
        Point bl = tl == sorted[0] ? sorted[1] : sorted[0];
        Point tr = sorted[2].y < sorted[3].y ? sorted[2] : sorted[3];
        Point br = tr == sorted[2] ? sorted[3] : sorted[2];
        return new Point[]{tl, tr, br, bl};
    }
    private static double distance(Point a, Point b) { return Math.hypot(a.x-b.x, a.y-b.y); }
    private static double area(Point[] q) {
        double sum = 0; for (int i=0;i<q.length;i++) { Point a=q[i],b=q[(i+1)%q.length]; sum += a.x*b.y-b.x*a.y; }
        return Math.abs(sum)*0.5;
    }
    private static Mat bgr(Bitmap image) {
        Mat rgba = new Mat(), bgr = new Mat(); Utils.bitmapToMat(image, rgba);
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR); rgba.release(); return bgr;
    }
    private static float[] tensor(Mat bgr, int width, int height) {
        Mat resized = new Mat(); Imgproc.resize(bgr, resized, new Size(width, height));
        int plane = width*height; byte[] pixels = new byte[plane*3]; resized.get(0,0,pixels); resized.release();
        float[] result = new float[pixels.length];
        for (int i=0;i<plane;i++) for (int c=0;c<3;c++) result[c*plane+i]=(pixels[i*3+c]&255)/255f;
        return result;
    }
    private float[][][][] predictCorners(Mat image) throws Exception {
        float[] data = tensor(image,256,256);
        try (OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(data),new long[]{1,3,256,256});
             OrtSession.Result result=aligner.run(Collections.singletonMap("img",input))) {
            return (float[][][][])result.get(0).getValue();
        }
    }
    @Override public Point[] detectPage(Bitmap image) throws Exception {
        Mat source=bgr(image);
        try {
            float[][][][] heatmaps=predictCorners(source);
            int h=source.rows(),w=source.cols();double postScale=Math.min(1.0,1280.0/Math.max(w,h));int postW=Math.max(1,(int)Math.round(w*postScale)),postH=Math.max(1,(int)Math.round(h*postScale)); Point[] points=new Point[4];
            for (int c=0;c<4;c++) {
                float[][] heat=heatmaps[0][c]; Mat small=new Mat(heat.length,heat[0].length,CvType.CV_32F);
                try {
                    for(int y=0;y<heat.length;y++) small.put(y,0,heat[y]);
                    Mat full=new Mat(),masked=new Mat(),binary=new Mat(),hierarchy=new Mat(); List<MatOfPoint> contours=new ArrayList<>();
                    try {
                        Imgproc.resize(small,full,new Size(postW,postH));
                        float[] values=new float[full.rows()*full.cols()];full.get(0,0,values);
                        for(int i=0;i<values.length;i++)if(values[i]<.3f)values[i]=0;
                        full.put(0,0,values);
                        full.convertTo(masked,CvType.CV_8U,255);
                        Imgproc.threshold(masked,binary,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);
                        Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
                        if(contours.isEmpty()) return null;
                        MatOfPoint largest=Collections.max(contours,Comparator.comparingDouble(Imgproc::contourArea));
                        Moments moments=Imgproc.moments(largest);
                        if(Math.abs(moments.get_m00())<1e-8) return null;
                        points[c]=new Point(moments.get_m10()/moments.get_m00()*w/postW,moments.get_m01()/moments.get_m00()*h/postH);
                    } finally {
                        full.release();masked.release();binary.release();hierarchy.release();for(MatOfPoint contour:contours)contour.release();
                    }
                } finally { small.release(); }
            }
            Point[] quad=order(points);
            MatOfPoint polygon=new MatOfPoint(quad);
            try {
                if(!Imgproc.isContourConvex(polygon)||area(quad)<.05*w*h) return null;
            } finally { polygon.release(); }
            return quad;
        } finally { source.release(); }
    }
    @Override public Bitmap rectifyPage(Bitmap image, Point[] corners) throws Exception {
        if(corners==null||corners.length!=4) return null;
        Mat source=bgr(image); Point[] q=order(corners);
        int width=(int)Math.round(Math.max(distance(q[0],q[1]),distance(q[2],q[3])));
        int height=(int)Math.round(Math.max(distance(q[0],q[3]),distance(q[1],q[2])));
        if(width<2||height<2){source.release();return null;}
        // Keep allocations bounded while retaining the full detail supported by the reader.
        double scale=Math.min(1.0,4096.0/Math.max(width,height));
        int outW=Math.max(2,(int)Math.round(width*scale)),outH=Math.max(2,(int)Math.round(height*scale));
        MatOfPoint2f from=new MatOfPoint2f(q),to=new MatOfPoint2f(new Point(0,0),new Point(outW-1,0),new Point(outW-1,outH-1),new Point(0,outH-1));
        Mat transform=Imgproc.getPerspectiveTransform(from,to),warped=new Mat();
        try {
            Imgproc.warpPerspective(source,warped,transform,new Size(outW,outH),Imgproc.INTER_LINEAR,Core.BORDER_REPLICATE);
            Bitmap result=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);Utils.matToBitmap(warped,result);return result;
        } finally {source.release();from.release();to.release();transform.release();warped.release();}
    }

    private static final class Read {
        final JSONObject decoded; final List<List<Double>> probabilities; final double mean;
        Read(JSONObject decoded,List<List<Double>> probabilities,double mean){this.decoded=decoded;this.probabilities=probabilities;this.mean=mean;}
    }
    private static double[] softmax(float[] logits) {
        double max=-Double.MAX_VALUE;for(float v:logits)max=Math.max(max,v);
        double sum=0;double[] out=new double[logits.length];for(int i=0;i<logits.length;i++){out[i]=Math.exp(logits[i]-max);sum+=out[i];}
        if(sum>0)for(int i=0;i<out.length;i++)out[i]/=sum;return out;
    }
    private static Read decodeLogits(float[][] logits,String[] alphabet,int eos,int pad,int separator) {
        List<String> lines=new ArrayList<>();lines.add("");List<List<Double>> scores=new ArrayList<>();scores.add(new ArrayList<>());double total=0;int count=0;
        StringBuilder joined=new StringBuilder();
        for(float[] row:logits){double[] probability=softmax(row);int index=0;for(int i=1;i<row.length;i++)if(row[i]>row[index])index=i;
            if(index==eos)break;if(index==pad)continue;
            if(index==separator){joined.append('&');lines.add("");scores.add(new ArrayList<>());continue;}
            if(index<0||index>=alphabet.length)continue;String token=alphabet[index];joined.append(token);lines.set(lines.size()-1,lines.get(lines.size()-1)+token);
            double p=probability[index];scores.get(scores.size()-1).add(p);total+=p;count++;
        }
        JSONObject decoded=MrzDecoder.decode(String.join("\n",lines));
        List<List<Double>> copy=new ArrayList<>();for(List<Double> row:scores)copy.add(new ArrayList<>(row));
        return new Read(decoded,copy,count==0?0:total/count);
    }
    private static float[] spotInput(Mat image) {
        int h=image.rows(),w=image.cols(),pad=Math.abs(h-w)/2;
        Mat padded=new Mat();
        if(h<w)Core.copyMakeBorder(image,padded,pad,pad,0,0,Core.BORDER_CONSTANT,new Scalar(0,0,0));
        else Core.copyMakeBorder(image,padded,0,0,pad,pad,Core.BORDER_CONSTANT,new Scalar(0,0,0));
        float[] values=tensor(padded,512,512);padded.release();return values;
    }
    private Read spot(Mat image) throws Exception {
        float[] data=spotInput(image);
        try(OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(data),new long[]{1,3,512,512});OrtSession.Result result=spotter.run(Collections.singletonMap("img",input))){
            return decodeLogits(((float[][][])result.get(0).getValue())[0],SPOT_CHARS,1,0,39);
        }
    }
    private Point[] detectMrz(Mat image) throws Exception {
        int h=image.rows(),w=image.cols(),pad=Math.abs(h-w)/2;Mat padded=new Mat();
        if(h<w)Core.copyMakeBorder(image,padded,pad,pad,0,0,Core.BORDER_CONSTANT,new Scalar(0,0,0));
        else Core.copyMakeBorder(image,padded,0,0,pad,pad,Core.BORDER_CONSTANT,new Scalar(0,0,0));
        float[] data=tensor(padded,256,256);
        try(OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(data),new long[]{1,3,256,256});OrtSession.Result result=detector.run(Collections.singletonMap("input",input))){
            float[][] prediction=((float[][][])result.get(0).getValue())[0];Mat small=new Mat(prediction.length,prediction[0].length,CvType.CV_32F);
            try {
                for(int y=0;y<prediction.length;y++)small.put(y,0,prediction[y]);
                Mat full=new Mat(),gray=new Mat(),binary=new Mat(),hierarchy=new Mat();List<MatOfPoint> contours=new ArrayList<>();
                try {
                    Imgproc.resize(small,full,new Size(padded.cols(),padded.rows()));full.convertTo(gray,CvType.CV_8U,255);
                    Imgproc.threshold(gray,binary,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);
                    Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
                    MatOfPoint selected=null;double max=0;for(MatOfPoint contour:contours){double a=Imgproc.contourArea(contour);if(a>max&&contour.total()>1){max=a;selected=contour;}}
                    if(selected==null||max<=0)return null;
                    MatOfPoint2f pts=new MatOfPoint2f(selected.toArray());RotatedRect rect;
                    try{rect=Imgproc.minAreaRect(pts);}finally{pts.release();}
                    Point[] raw=new Point[4];rect.points(raw);Point[] quad=order(raw);
                    for(Point point:quad){point.x-=h<w?0:pad;point.y-=h<w?pad:0;}
                    return quad;
                } finally {full.release();gray.release();binary.release();hierarchy.release();for(MatOfPoint contour:contours)contour.release();}
            } finally {small.release();}
        } finally {padded.release();}
    }
    private static Mat warpMrz(Mat image,Point[] q) {
        Point[] ordered=order(q);MatOfPoint2f points=new MatOfPoint2f(ordered);
        RotatedRect rect=Imgproc.minAreaRect(points);Point[] raw=new Point[4];rect.points(raw);raw=order(raw);
        double sideA=distance(raw[0],raw[1]),sideB=distance(raw[0],raw[3]);
        int width=(int)Math.round(Math.max(sideA,sideB)),height=(int)Math.round(Math.min(sideA,sideB));
        points.release();if(width<2||height<2)return null;
        MatOfPoint2f src=new MatOfPoint2f(raw),dst=new MatOfPoint2f(new Point(0,0),new Point(width,0),new Point(width,height),new Point(0,height));
        Mat transform=Imgproc.getPerspectiveTransform(src,dst),crop=new Mat();
        try{Imgproc.warpPerspective(image,crop,transform,new Size(width,height));return crop;}
        finally{src.release();dst.release();transform.release();}
    }
    private Read recognizeSpecialist(Mat crop) throws Exception {
        float[] data=tensor(crop,640,64);
        try(OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(data),new long[]{1,3,64,640});OrtSession.Result result=recognizer.run(Collections.singletonMap("input",input))){
            return decodeLogits(((float[][][])result.get(0).getValue())[0],SPECIAL_CHARS,1,0,2);
        }
    }
    private static double nameMinimum(JSONObject decoded,List<List<Double>> scores) {
        JSONArray lines=decoded.optJSONArray("lines");
        if(lines==null||lines.length()!=2||scores.size()!=2||lines.optString(0).length()!=44||lines.optString(1).length()!=44||scores.get(0).size()<44)return 0;
        double min=1;for(int i=5;i<44;i++)min=Math.min(min,scores.get(0).get(i));return min;
    }
    private static boolean isTd3(JSONObject decoded) {
        JSONArray lines=decoded.optJSONArray("lines");return lines!=null&&lines.length()==2&&lines.optString(0).length()==44&&lines.optString(1).length()==44;
    }
    @Override public JSONObject scanPage(Bitmap rectifiedPage) throws Exception {
        long start=SystemClock.elapsedRealtime();Mat image=bgr(rectifiedPage);long spotting=0,fallback=0;String source="spotter";Read chosen;
        try {
            long t=SystemClock.elapsedRealtime();Read direct=spot(image);spotting=SystemClock.elapsedRealtime()-t;chosen=direct;
            if(!"valid".equals(direct.decoded.optString("status"))||!isTd3(direct.decoded)){
                source="specialist_fallback";t=SystemClock.elapsedRealtime();Point[] q=detectMrz(image);fallback=SystemClock.elapsedRealtime()-t;
                if(q!=null){Mat crop=warpMrz(image,q);if(crop!=null){try{t=SystemClock.elapsedRealtime();chosen=recognizeSpecialist(crop);fallback+=SystemClock.elapsedRealtime()-t;}finally{crop.release();}}
                    else chosen=new Read(new JSONObject().put("status","no_mrz"),new ArrayList<>(),0);
                } else chosen=new Read(new JSONObject().put("status","no_mrz"),new ArrayList<>(),0);
            }
            JSONObject out=chosen.decoded;double nameMin=nameMinimum(out,chosen.probabilities);
            boolean confidenceOk=direct.mean>=SPOT_MEAN_GATE||nameMin>=NAME_MIN_GATE;
            boolean valid="valid".equals(out.optString("status"))&&isTd3(out);
            out.put("rawText",String.join("\n",lines(out))).put("engine","aligned MRZ cascade");
            out.put("spotterMeanProbability",direct.mean).put("chosenNameMinimumProbability",nameMin);
            out.put("confidenceGatePassed",confidenceOk).put("recognitionSource",source);
            if(!valid||!confidenceOk){
                out.put("status","retake").put("warning",valid?"The text read is not reliable enough. Move closer, keep the whole page in frame, hold steady and avoid glare, then capture again.":"The MRZ could not be read reliably. Move closer, keep the page flat and in focus, and avoid glare.");
                out.remove("fields");
            }
            JSONObject times=new JSONObject().put("spotting",spotting).put("fallback",fallback).put("total",SystemClock.elapsedRealtime()-start);
            out.put("timingsMs",times).put("imageWidth",rectifiedPage.getWidth()).put("imageHeight",rectifiedPage.getHeight());
            return out;
        } finally {image.release();}
    }
    private static List<String> lines(JSONObject result) {
        List<String> out=new ArrayList<>();JSONArray values=result.optJSONArray("lines");if(values!=null)for(int i=0;i<values.length();i++)out.add(values.optString(i));return out;
    }
    @Override public void close(){for(OrtSession session:new OrtSession[]{aligner,spotter,detector,recognizer})try{session.close();}catch(Exception ignored){}}
}
