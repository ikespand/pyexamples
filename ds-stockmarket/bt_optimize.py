# Interactive sessions cannot use the multiprocessing module, so we need to run this script in a separate process.
# So, we can use this script to optimize the strategy parameters and get the best parameters for the strategy.
import pandas as pd
import numpy as np
from backtesting import Backtest, Strategy
from backtesting.lib import crossover
from backtesting.test import SMA, GOOG
import talib 
from utils import get_stock_data

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

class RsiWmaBollingerStrategy(Strategy):
    rsi_period = 14
    rsi_buy = 30
    rsi_sell = 70
    bb_period = 20
    bb_std = 2
    stop_loss_pct = 0.02
    take_profit_pct = 0.05

    def init(self):
        close = self.data.Close

        # Use TA-Lib for all indicators (multiprocessing safe)
        self.rsi = talib.RSI(close, timeperiod=self.rsi_period)
        self.wma = talib.WMA(close, timeperiod=self.bb_period)
        self.std = pd.Series(close).rolling(self.bb_period).std().values

        self.bb_upper = self.wma + self.bb_std * self.std
        self.bb_lower = self.wma - self.bb_std * self.std

    def next(self):
        i = len(self.data.Close) - 1
        price = self.data.Close[i]

        # Handle edge cases
        if np.isnan(self.rsi[i]) or np.isnan(self.bb_lower[i]) or np.isnan(self.bb_upper[i]):
            return

        if self.rsi[i] < self.rsi_buy and price < self.bb_lower[i]:
            sl = price * (1 - self.stop_loss_pct)
            tp = price * (1 + self.take_profit_pct)
            self.buy(sl=sl, tp=tp)

        elif self.position:
            if self.rsi[i] > self.rsi_sell or price > self.bb_upper[i]:
                self.position.close()


if __name__ == "__main__":
    # Load sample data
    df = get_stock_data("NFLX", period="60d")
    # Run Backtest with Optimization
    bt = Backtest(df, RsiWmaBollingerStrategy, cash=10_000, commission=0.002)
    # Run optimization
    optimized_stats = bt.optimize(
        rsi_period=range(10, 21, 2),
        rsi_buy=range(20, 40, 2),
        rsi_sell=range(60, 80, 2),
        bb_period=range(10, 31, 5),
        bb_std=np.arange(1.5, 2.6, 0.1).tolist(),          # <- fix here
        #stop_loss_pct=np.arange(0.01, 0.05, 0.01).tolist(), # <- and here
        #take_profit_pct=np.arange(0.03, 0.10, 0.01).tolist(), # <- and here
        maximize='Equity Final [$]',
        constraint=lambda p: p.rsi_buy < p.rsi_sell
    )

    # Plot results of optimized strategy
    #bt.plot()
    print("\n Optimized Strategy Performance:")
    print(optimized_stats)
    # Print optimized parameters and results
    print("\n Best Parameters Found:")
    print(optimized_stats._strategy)

   

