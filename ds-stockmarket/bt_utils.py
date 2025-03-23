import yfinance 

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