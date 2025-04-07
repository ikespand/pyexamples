import pandas as pd
import time
import yfinance as yf
from datetime import datetime, timedelta
from backtesting import Backtest, Strategy
import pandas_ta as ta
from utils import get_stock_data

# Helper function to pad the indicator to match data length
def pad_indicator(ind, total_len):
    """Pads a numpy array with NaNs to match the full length of price data."""
    ind = np.asarray(ind)
    pad_len = total_len - len(ind)
    if pad_len < 0:
        raise ValueError("Indicator length exceeds data length")
    return np.concatenate([np.full(pad_len, np.nan), ind])

# Initialize trade log CSV
def initialize_trade_log(file_name="paper_trades.csv"):
    if not pd.io.common.file_exists(file_name):
        trade_data = pd.DataFrame(columns=["timestamp", "action", "price", "stop_loss", "take_profit", "position_size", "exit_price", "duration"])
        trade_data.to_csv(file_name, index=False)
    return pd.read_csv(file_name)

# Log trades to CSV
def log_trade(file_name, action, price, stop_loss, take_profit, position_size, exit_price=None, duration=None):
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    trade_data = pd.read_csv(file_name)
    new_trade = pd.DataFrame([[timestamp, action, price, stop_loss, take_profit, position_size, exit_price, duration]],
                             columns=["timestamp", "action", "price", "stop_loss", "take_profit", "position_size", "exit_price", "duration"])
    trade_data = pd.concat([trade_data, new_trade], ignore_index=True)
    trade_data.to_csv(file_name, index=False)

def paper_trade_strategy(ticker, file_name="paper_trades.csv", stop_loss_pct=0.02, take_profit_pct=0.05, interval="15m"):
    print(f"Starting paper trading for {ticker} (interval: {interval})...")
    trade_log = initialize_trade_log(file_name)

    while True:
        try:
            df = get_stock_data(ticker=ticker, interval=interval, period="2d")
            print(f"Fetched data for {ticker}...")
            if df.empty or len(df) < 20:
                print("Not enough data yet, waiting...")
                time.sleep(60)
                continue

            # Use latest candle
            latest_idx = -2  # -1 may still be forming
            current_data = df.iloc[latest_idx]
            price = current_data['Close']

            # Generate indicators
            bb = df.ta.bbands(length=20, close='Close')
            rsi_series = df.ta.rsi(length=14, close='Close')

            action, stop_loss, take_profit = None, None, None

            if price < bb['BBL_20_2.0'].iloc[latest_idx] and rsi_series.iloc[latest_idx] < 30:
                action = "buy"
                stop_loss = price * (1 - stop_loss_pct)
                take_profit = price * (1 + take_profit_pct)

            elif price > bb['BBU_20_2.0'].iloc[latest_idx] and rsi_series.iloc[latest_idx] > 70:
                action = "sell"

            if action == "buy":
                position_size = 1
                log_trade(file_name, action="buy", price=price, stop_loss=stop_loss,
                          take_profit=take_profit, position_size=position_size)
                
                # Monitor for exit condition (SL or TP)
                for j in range(latest_idx + 1, len(df)):
                    next_data = df.iloc[j]
                    next_price = next_data['Close']
                    if next_price <= stop_loss or next_price >= take_profit:
                        duration = (next_data.name - current_data.name).total_seconds() / 60
                        log_trade(file_name, action="sell", price=next_price, stop_loss=stop_loss,
                                  take_profit=take_profit, position_size=position_size,
                                  exit_price=next_price, duration=f"{int(duration)}min")
                        break

            # Wait before next check (based on interval)
            time.sleep(300)  # you can set this to 300 for 5m or 900 for 15m intervals

        except KeyboardInterrupt:
            print("\nPaper trading stopped by user.")
            break
        except Exception as e:
            print(f"Error: {e}")
            time.sleep(60)

if __name__ == "__main__":
    ticker = "BTC-USD"
    interval = "5m" 
    paper_trade_strategy(ticker=ticker, file_name="paper_trades.csv", interval=interval)
