# Interactive sessions cannot use the multiprocessing module, so we need to run this script in a separate process.
# So, we can use this script to optimize the strategy parameters and get the best parameters for the strategy.
import pandas as pd
import numpy as np
from backtesting import Backtest, Strategy
from backtesting.lib import crossover
from backtesting.test import SMA, GOOG
import talib 
from bt_utils import get_stock_data

def optimize_profit_with_limited_trades(stats):
    """
    Custom optimization function to maximize net profit while limiting trades.
    
    :param stats: Backtest statistics dictionary
    :return: Custom score
    """
    profit = stats.get("Equity Final", 0) - stats.get("Equity Start", 0)
    trade_count = stats.get("# Trades", 0)
    # Penalize excessive trades (if more than 50 as an example)
    trade_penalty = max(0, (trade_count - 50) * 100)
    return profit - trade_penalty  # Maximize net profit with limited trades

def optimize_profit_per_trade(stats):
    """
    Custom optimization function to maximize profit per trade.
    
    :param stats: Backtest statistics dictionary
    :return: Profit per trade score
    """
    profit = stats.get("Equity Final", 0) - stats.get("Equity Start", 0)
    trade_count = stats.get("# Trades", 1)  # Avoid division by zero
    return profit / trade_count


class RsiSmaStrategy(Strategy):
    short_window = 10  # Short-term SMA
    long_window = 20   # Long-term SMA
    rsi_period = 14    # RSI period
    rsi_buy = 30       # RSI oversold threshold
    rsi_sell = 70      # RSI overbought threshold
    stop_loss_pct = 0.02  # 2% stop-loss
    take_profit_pct = 0.05  # 5% take-profit

    def init(self):
        # Compute SMAs
        self.sma_short = self.I(SMA, self.data.Close, self.short_window)
        self.sma_long = self.I(SMA, self.data.Close, self.long_window)
        # Compute RSI using TA-Lib
        self.rsi = self.I(talib.RSI, self.data.Close, self.rsi_period)

    def next(self):
        # Entry Condition: RSI below 30 & SMA crossover
        if self.rsi[-1] < self.rsi_buy and crossover(self.sma_short, self.sma_long):
            price = self.data.Close[-1]
            sl = price * (1 - self.stop_loss_pct)  # Stop-Loss 2% below entry
            tp = price * (1 + self.take_profit_pct)  # Take-Profit 5% above entry
            self.buy(sl=sl, tp=tp)  # Place order with SL & TP

        # Exit Condition: RSI above 70 OR SMA crossover in opposite direction
        elif self.rsi[-1] > self.rsi_sell or crossover(self.sma_long, self.sma_short):
            self.position.close()

if __name__ == "__main__":
    # Load sample data
    df = get_stock_data("QCOM", interval="1d")
    # Run Backtest with Optimization
    bt = Backtest(df, RsiSmaStrategy, cash=10000, commission=0.002)
    optimized_stats = bt.optimize(
        short_window=range(5, 20, 5), 
        long_window=range(10, 50, 10),
        rsi_period=range(10, 20, 1),
        rsi_buy=range(20, 40, 10),
        rsi_sell=range(60, 80, 10),
        maximize=optimize_profit_with_limited_trades,  # Or any in-built metrics which appears in output
        constraint=lambda params: params.short_window < params.long_window,  # Ensure valid SMA crossover
       #return_heatmap=True
    )

    # Plot results of optimized strategy
    #bt.plot()
    print("\n Optimized Strategy Performance:")
    print(optimized_stats)
    # Print optimized parameters and results
    print("\n Best Parameters Found:")
    print(optimized_stats._strategy)

   

