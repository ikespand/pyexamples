import pandas as pd
import time
import yfinance as yf
from datetime import datetime, timedelta
from backtesting import Backtest, Strategy
import pandas_ta as ta
from utils import get_stock_data
import numpy as np

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

def rsi_wma_bollinger_signals(df, idx=-2, rsi_period=14, bb_period=20, bb_std=2, sl_pct=0.02, tp_pct=0.05):
    # Prevent errors if not enough data
    if len(df) < bb_period + 1:
        return None

    rsi = df.ta.rsi(length=rsi_period)
    wma = df.ta.wma(length=bb_period)
    std = df['Close'].rolling(bb_period).std()
    bb_upper = wma + bb_std * std
    bb_lower = wma - bb_std * std

    # Make sure we're looking at valid index
    if idx < -len(df):
        return None

    price = df['Close'].iloc[idx]
    rsi_val = rsi.iloc[idx]
    lower_bb = bb_lower.iloc[idx]
    upper_bb = bb_upper.iloc[idx]

    # Skip if indicators are NaN
    if pd.isna(rsi_val) or pd.isna(lower_bb) or pd.isna(upper_bb):
        return None

    # Buy signal
    if rsi_val < 30 and price < lower_bb:
        return {
            "action": "buy",
            "price": price,
            "stop_loss": price * (1 - sl_pct),
            "take_profit": price * (1 + tp_pct)
        }

    # Sell signal
    elif rsi_val > 70 or price > upper_bb:
        return {
            "action": "sell",
            "price": price
        }

    return None

def paper_trade_strategy(ticker, file_name="paper_trades.csv", stop_loss_pct=0.02, take_profit_pct=0.05, interval="15m"):
    print(f"Starting paper trading for {ticker} (interval: {interval})...")
    trade_log = initialize_trade_log(file_name)

    while True:
        try:
            df = get_stock_data(ticker=ticker, interval=interval, period="5d")
            print(df.head(1))
            # Flatten MultiIndex columns (yfinance quirk)
            if isinstance(df.columns, pd.MultiIndex):
                df.columns = ['_'.join(col).strip() if isinstance(col, tuple) else col for col in df.columns]

            # Flatten MultiIndex index
            if isinstance(df.index, pd.MultiIndex):
                df.reset_index(inplace=True)
                if 'Datetime' in df.columns:
                    df.set_index('Datetime', inplace=True)

            print("DataFrame OK ✅", df.columns)


            if df.empty or len(df) < 30:
                print("Not enough data yet, waiting...")
                time.sleep(60)
                continue

            signal = rsi_wma_bollinger_signals(
                df,
                idx=-2,
                rsi_period=14,
                bb_period=20,
                bb_std=2,
                sl_pct=stop_loss_pct,
                tp_pct=take_profit_pct
            )

            if not signal:
                print("No signal detected.")
            elif signal["action"] == "buy":
                print(f"Buy signal at {signal['price']}, SL: {signal['stop_loss']}, TP: {signal['take_profit']}")

                log_trade(file_name, action="buy", price=signal["price"],
                          stop_loss=signal["stop_loss"], take_profit=signal["take_profit"],
                          position_size=1)

                # Simulate price movement — this will eventually be replaced with real-time price feed
                for j in range(-1, -len(df) - 1, -1):
                    current_price = df['Close'].iloc[j]
                    if current_price <= signal["stop_loss"] or current_price >= signal["take_profit"]:
                        duration = (df.index[-1] - df.index[j]).total_seconds() / 60
                        log_trade(file_name, action="sell", price=current_price,
                                  stop_loss=signal["stop_loss"], take_profit=signal["take_profit"],
                                  position_size=1, exit_price=current_price, duration=f"{int(duration)}min")
                        break

            elif signal["action"] == "sell":
                print(f"Sell signal at {signal['price']}")
                log_trade(file_name, action="sell", price=signal["price"],
                          stop_loss=None, take_profit=None, position_size=0)

            time.sleep(300)  # wait 5 minutes

        except KeyboardInterrupt:
            print("\nPaper trading stopped by user.")
            break
        except Exception as e:
            print(f"Error: {e}")
            time.sleep(60)


if __name__ == "__main__":
    ticker = "MSFT"
    interval = "15m" 
    paper_trade_strategy(ticker=ticker, file_name="paper_trades.csv", interval=interval)