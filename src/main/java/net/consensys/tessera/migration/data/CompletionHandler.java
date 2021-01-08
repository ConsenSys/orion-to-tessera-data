package net.consensys.tessera.migration.data;

import java.util.concurrent.CountDownLatch;

public class CompletionHandler extends AbstractEventHandler {

    private CountDownLatch countDownLatch;

    public CompletionHandler(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void onEvent(OrionRecordEvent event) throws Exception {
        event.reset();
        countDownLatch.countDown();
    }
}
