package com.jbooktrader.strategy;

import com.jbooktrader.indicator.velocity.*;
import com.jbooktrader.platform.indicator.*;
import com.jbooktrader.platform.model.*;
import com.jbooktrader.platform.optimizer.*;
import com.jbooktrader.strategy.base.*;

/**
 *
 */
public class Defender1 extends StrategyES {

    // Technical indicators
    private final Indicator balanceVelocityInd, trendStrengthVelocityInd;

    // Strategy parameters names
    private static final String FAST_PERIOD = "Fast Period";
    private static final String SLOW_PERIOD = "Slow Period";
    private static final String TREND_PERIOD = "Trend Period";
    private static final String ENTRY = "Entry";


    // Strategy parameters values
    private final int entry;

    public Defender1(StrategyParams optimizationParams) throws JBookTraderException {
        super(optimizationParams);

        entry = getParam(ENTRY);
        balanceVelocityInd = new BalanceVelocity(getParam(FAST_PERIOD), getParam(SLOW_PERIOD));
        trendStrengthVelocityInd = new TrendStrengthVelocity(getParam(TREND_PERIOD));
        addIndicator(balanceVelocityInd);
        addIndicator(trendStrengthVelocityInd);
    }

    /**
     * Adds parameters to strategy. Each parameter must have 5 values:
     * name: identifier
     * min, max, step: range for optimizer
     * value: used in backtesting and trading
     */
    @Override
    public void setParams() {
        addParam(FAST_PERIOD, 4, 18, 1, 9);
        addParam(SLOW_PERIOD, 2500, 4500, 100, 3380);
        addParam(TREND_PERIOD, 250, 800, 10, 585);
        addParam(ENTRY, 120, 200, 1, 170);
    }

    /**
     * Framework invokes this method when a new snapshot of the limit order book is taken
     * and the technical indicators are recalculated. This is where the strategy itself
     * (i.e., its entry and exit conditions) should be defined.
     */
    @Override
    public void onBookSnapshot() {
        double balanceVelocity = balanceVelocityInd.getValue() * 10;
        double trendStrengthVelocity = trendStrengthVelocityInd.getValue();

        int currentPosition = getPositionManager().getPosition();
        if (currentPosition > 0 && balanceVelocity <= -entry) {
            setPosition(0);
        }
        if (currentPosition < 0 && balanceVelocity >= entry) {
            setPosition(0);
        }

        if (trendStrengthVelocity < 0) {
            if (balanceVelocity >= entry) {
                setPosition(1);
            } else if (balanceVelocity <= -entry) {
                setPosition(-1);
            }
        }

    }
}