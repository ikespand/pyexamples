import yfinance 
import numpy as np
import dill as pickle
from sklearn.preprocessing import MinMaxScaler

def get_stock_data(ticker="NVDA", interval="1d", start="2022-01-01", end="2024-03-01"):
    """
    Fetch stock data for any ticker and candle interval.
    
    :param ticker: Stock symbol (e.g., "NVDA", "AAPL", "MSFT")
    :param interval: Candle timeframe (e.g., "1d", "1h", "15m")
    :param start: Start date for historical data
    :param end: End date for historical data
    :return: Cleaned stock DataFrame
    """
    df = yfinance.download(ticker, interval=interval, start=start, end=end)
    
    df = df[['Open', 'High', 'Low', 'Close', 'Volume']].copy()
    df.columns = ["Open", "High", "Low", "Close", "Volume"]
    return df

#%% 
class StockPricePredictor:
    def __init__(self, model_path="stock_esn_model.pkl", scaler_path="scaler_esn_model.pkl"):
        # Load the trained model
        with open(model_path, "rb") as f:
            self.model = pickle.load(f)
        
        # Load the saved scaler
        with open(scaler_path, "rb") as f:
            self.scaler = pickle.load(f)
    
    def preprocess_input(self, last_n_steps_df):
        """Scales the input dataframe before making predictions."""
        scaled_input = self.scaler.transform(last_n_steps_df)
        return scaled_input.flatten().reshape(1, -1)  # Reshape for model
    
    def predict(self, last_n_steps_df):
        """Predicts the next day's close price given the last n_steps data."""
        x_input = self.preprocess_input(last_n_steps_df)
        y_pred_scaled = self.model.predict(x_input)
        
        # Inverse transform (handling single feature case)
        y_pred_scaled_n = np.repeat(y_pred_scaled, 4, axis=1)
        y_pred = self.scaler.inverse_transform(y_pred_scaled_n)[:, 0]
        return y_pred[0]
# %%
if __name__ == "__main__":
    # Load model & scaler
    predictor = StockPricePredictor(model_path="stock_esn_model_7.pkl", 
                                    scaler_path="scaler_esn_model_7.pkl")
    # Example input (last 4 days' data as a DataFrame)
    import pandas as pd
    example_data = pd.DataFrame({
        "Open": [150, 152, 151, 153, 152, 151, 153],
        "High": [155, 157, 156, 158, 157, 156, 158],
        "Low": [148, 150, 149, 151, 150, 149, 151],
        "Close": [152, 154, 153, 155, 154, 153, 155]
    })
    predicted_close = predictor.predict(example_data)
    print(f"Predicted Close Price: {predicted_close}")

# %%
