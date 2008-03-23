package com.jbooktrader.platform.optimizer;

import com.jbooktrader.platform.backtest.BackTestFileReader;
import com.jbooktrader.platform.marketdepth.*;
import com.jbooktrader.platform.model.*;
import com.jbooktrader.platform.performance.PerformanceManager;
import com.jbooktrader.platform.report.Report;
import com.jbooktrader.platform.schedule.TradingSchedule;
import com.jbooktrader.platform.strategy.Strategy;
import com.jbooktrader.platform.util.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.text.NumberFormat;
import java.util.*;

/**
 * Runs a trading strategy in the optimizer mode using a data file containing
 * historical market depth.
 */
public abstract class OptimizerRunner implements Runnable {
    protected static final long MAX_HISTORY_PERIOD = 24 * 60 * 60 * 1000L;// 24 hours
    protected static final long MAX_ITERATIONS = 50000000L;
    protected static final int MAX_RESULTS = 5000;
    protected static final long UPDATE_FREQUENCY = 1000000L;// lines
    public final List<Result> results;
    protected final OptimizerDialog optimizerDialog;
    protected final NumberFormat nf2;
    protected boolean cancelled;
    protected ResultComparator resultComparator;
    protected final StrategyParams strategyParams;
    protected final String strategyName;
    protected ComputationalTimeEstimator timeEstimator;
    protected final Constructor<?> strategyConstructor;
    protected final TradingSchedule tradingSchedule;
    public BackTestFileReader backTestFileReader;
    protected int lineCount;
    public MarketBook marketBook;
    protected final int minTrades;
    private long completedSteps, totalSteps;

    public OptimizerRunner(OptimizerDialog optimizerDialog, Strategy strategy, StrategyParams params) throws ClassNotFoundException, NoSuchMethodException {
        this.optimizerDialog = optimizerDialog;
        this.strategyName = strategy.getName();
        this.strategyParams = params;
        tradingSchedule = strategy.getTradingSchedule();
        results = Collections.synchronizedList(new ArrayList<Result>());
        nf2 = NumberFormatterFactory.getNumberFormatter(2);
        Class<?> clazz = Class.forName(strategy.getClass().getName());
        Class<?>[] parameterTypes = new Class[]{StrategyParams.class, MarketBook.class};
        strategyConstructor = clazz.getConstructor(parameterTypes);
        resultComparator = new ResultComparator(optimizerDialog.getSortCriteria());
        marketBook = new MarketBook();
        minTrades = optimizerDialog.getMinTrades();
    }

    abstract public void optimize() throws Exception;

    public void setTotalSteps(long totalSteps) {
        this.totalSteps = totalSteps;
        if (timeEstimator == null) {
            timeEstimator = new ComputationalTimeEstimator(System.currentTimeMillis(), totalSteps);
        }
        timeEstimator.setTotalIterations(totalSteps);
    }


    public void execute(List<Strategy> strategies) throws JBookTraderException {
        backTestFileReader.reset();
        marketBook.getAll().clear();

        MarketDepth marketDepth;
        while ((marketDepth = backTestFileReader.getNextMarketDepth()) != null) {

            marketBook.add(marketDepth);
            long time = marketDepth.getTime();
            boolean inSchedule = tradingSchedule.contains(time);

            for (Strategy strategy : strategies) {

                strategy.updateIndicators();
                if (strategy.hasValidIndicators()) {
                    strategy.onBookChange();
                }

                if (!inSchedule) {
                    strategy.closePosition();// force flat position
                }

                strategy.getPositionManager().trade();
                strategy.trim(time - MAX_HISTORY_PERIOD);

                completedSteps++;
                if (completedSteps % UPDATE_FREQUENCY == 0) {
                    showFastProgress(completedSteps, totalSteps, "Optimizing");
                }
                if (cancelled) {
                    return;
                }
            }
        }

        for (Strategy strategy : strategies) {
            strategy.closePosition();
            strategy.getPositionManager().trade();

            PerformanceManager performanceManager = strategy.getPerformanceManager();
            int trades = performanceManager.getTrades();

            if (trades >= minTrades) {
                Result result = new Result(strategy.getParams(), performanceManager);

                if (!results.contains(result)) {
                    results.add(result);
                }

                showProgress(completedSteps, totalSteps, "Optimizing");
            }
        }
    }


    public void cancel() {
        cancelled = true;
    }

    public void saveToFile() throws IOException, JBookTraderException {
        if (results.size() == 0) {
            return;
        }

        Report.enable();
        String fileName = strategyName + "Optimizer";
        Report optimizerReport = new Report(fileName);

        optimizerReport.reportDescription("Strategy parameters:");
        for (StrategyParam param : strategyParams.getAll()) {
            optimizerReport.reportDescription(param.toString());
        }
        optimizerReport.reportDescription("Minimum trades for strategy inclusion: " + optimizerDialog.getMinTrades());
        optimizerReport.reportDescription("Back data file: " + optimizerDialog.getFileName());

        List<String> otpimizerReportHeaders = new ArrayList<String>();
        StrategyParams params = results.iterator().next().getParams();
        for (StrategyParam param : params.getAll()) {
            otpimizerReportHeaders.add(param.getName());
        }

        otpimizerReportHeaders.add("Total P&L");
        otpimizerReportHeaders.add("Max Drawdown");
        otpimizerReportHeaders.add("Trades");
        otpimizerReportHeaders.add("Profit Factor");
        otpimizerReportHeaders.add("True Kelly");
        optimizerReport.report(otpimizerReportHeaders);

        for (Result result : results) {
            params = result.getParams();

            List<String> columns = new ArrayList<String>();
            for (StrategyParam param : params.getAll()) {
                columns.add(nf2.format(param.getValue()));
            }

            columns.add(nf2.format(result.getNetProfit()));
            columns.add(nf2.format(result.getMaxDrawdown()));
            columns.add(nf2.format(result.getTrades()));
            columns.add(nf2.format(result.getProfitFactor()));
            columns.add(nf2.format(result.getTrueKelly()));

            optimizerReport.report(columns);
        }
        Report.disable();
    }

    public void showProgress(long counter, long numberOfTasks, String text) {
        synchronized (results) {
            Collections.sort(results, resultComparator);

            while (results.size() > MAX_RESULTS) {
                results.remove(results.size() - 1);
            }

            optimizerDialog.setResults(results);
        }

        String remainingTime = timeEstimator.getTimeLeft(counter);
        optimizerDialog.setProgress(counter, numberOfTasks, text, remainingTime);
    }

    public void showFastProgress(long counter, long numberOfTasks, String text) {
        String remainingTime = (counter == numberOfTasks) ? "00:00:00" : timeEstimator.getTimeLeft(counter);
        optimizerDialog.setProgress(counter, numberOfTasks, text, remainingTime);
    }


    public void run() {
        try {

            results.clear();
            optimizerDialog.setResults(results);
            optimizerDialog.enableProgress();
            optimizerDialog.showProgress("Scanning historical data file...");
            backTestFileReader = new BackTestFileReader(optimizerDialog.getFileName());
            lineCount = backTestFileReader.getTotalLineCount();

            if (cancelled) {
                return;
            }

            optimizerDialog.showProgress("Starting optimization...");
            long start = System.currentTimeMillis();

            optimize();

            if (!cancelled) {
                showFastProgress(100, 100, "Optimization");
                saveToFile();
                long totalTimeInSecs = (System.currentTimeMillis() - start) / 1000;
                MessageDialog.showMessage(optimizerDialog, "Optimization completed successfully in " + totalTimeInSecs + " seconds.");
            }
        } catch (Throwable t) {
            Dispatcher.getReporter().report(t);
            MessageDialog.showError(optimizerDialog, t.toString());
        } finally {
            optimizerDialog.signalCompleted();
        }
    }
}
