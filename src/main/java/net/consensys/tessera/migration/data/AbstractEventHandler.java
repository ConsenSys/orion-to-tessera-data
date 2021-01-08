package net.consensys.tessera.migration.data;

import com.lmax.disruptor.EventHandler;

public abstract class AbstractEventHandler implements EventHandler<OrionRecordEvent> {

    private static final System.Logger LOGGER = System.getLogger(AbstractEventHandler.class.getName());

    @Override
    public final void onEvent(OrionRecordEvent event, long sequence, boolean endOfBatch) throws Exception {
        LOGGER.log(System.Logger.Level.INFO,String.format("Enter %s",event));
        try {
            this.onEvent(event);
            LOGGER.log(System.Logger.Level.INFO,String.format("Exit %s",event));
        } catch (Throwable ex) {
            LOGGER.log(System.Logger.Level.ERROR,ex);
        }
    }

    public abstract void onEvent(OrionRecordEvent event) throws Exception;
}
