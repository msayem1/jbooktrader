package com.jbooktrader.strategy;

import com.ib.client.Contract;
import com.jbooktrader.indicator.DepthBalance;
import com.jbooktrader.platform.indicator.Indicator;
import com.jbooktrader.platform.model.JBookTraderException;
import com.jbooktrader.platform.optimizer.StrategyParams;
import com.jbooktrader.platform.schedule.TradingSchedule;
import com.jbooktrader.platform.strategy.Strategy;
import com.jbooktrader.platform.util.ContractFactory;

/**
 *
 */
public class EtfFlipper extends Strategy {

    // Technical indicators
    private final Indicator depthBalanceInd;

    // Strategy parameters names
    private static final String ENTRY = "Entry";
    private static final String EXIT = "Exit";

    // Strategy parameters values
    private final double entry, exit;


    public EtfFlipper(StrategyParams params) throws JBookTraderException {
        // Specify the contract to trade
        Contract contract = ContractFactory.makeStockContract("QQQQ", "SMART");
        // Define trading schedule
        TradingSchedule tradingSchedule = new TradingSchedule("9:35", "15:55", "America/New_York");
        int multiplier = 1;// contract multiplier
        double commissionRate = 0.005;// commission per share
        setStrategy(contract, tradingSchedule, multiplier, commissionRate);

        // Initialize strategy parameter values. If the strategy is running in the optimization
        // mode, the parameter values will be taken from the "params" object. Otherwise, the
        // "params" object will be empty and the parameter values will be initialized to the
        // specified default values.
        entry = params.get(ENTRY, 90);
        exit = params.get(EXIT, 80);

        // Create technical indicators
        depthBalanceInd = new DepthBalance(marketBook);

        // Specify the title and the chart number for each indicator
        // "0" = the same chart as the price chart; "1+" = separate subchart (below the price chart)
        addIndicator("Depth Balance", depthBalanceInd, 1);
    }

    /**
     * Returns min/max/step values for each strategy parameter. This method is
     * invoked by the strategy optimizer to obtain the strategy parameter ranges.
     */
    @Override
    public StrategyParams initParams() {
        StrategyParams params = new StrategyParams();
        params.add(ENTRY, 20, 70, 1);
        params.add(EXIT, 0, 60, 1);
        return params;
    }

    /**
     * This method is invoked by the framework when an order book changes and the technical
     * indicators are recalculated. This is where the strategy itself should be defined.
     */
    @Override
    public void onBookChange() {
        int currentPosition = getPositionManager().getPosition();
        double depthBalance = depthBalanceInd.getValue();
        if (depthBalance >= entry) {
            setPosition(1000);
        } else if (depthBalance <= -entry) {
            setPosition(-1000);
        } else {
            boolean target = (currentPosition > 0 && depthBalance <= -exit);
            target = target || (currentPosition < 0 && depthBalance >= exit);
            if (target) {
                setPosition(0);
            }
        }
    }
}