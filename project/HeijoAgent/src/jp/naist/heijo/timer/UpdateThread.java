package jp.naist.heijo.timer;

import java.io.IOException;
import java.util.Map;

import jp.naist.heijo.Monitor;
import jp.naist.heijo.debug.DebugValue;
import jp.naist.heijo.debug.IntervalPrinter;
import jp.naist.heijo.message.ExeTimeInfo;
import jp.naist.heijo.message.Message;
import jp.naist.heijo.util.Pair;

public class UpdateThread extends Thread
{

  private boolean isFirstSend = true;

  private long before = -1;

  private IntervalPrinter debugIntervalPrinter = null;

  public UpdateThread()
  {
    if (DebugValue.DEBUG_FLAG && DebugValue.DEBUG_PRINT_UPDATE_INTERVAL_FLAG) {
      debugIntervalPrinter = new IntervalPrinter(DebugValue.DEBUG_PRINT_UPDATE_INTERVAL_TIME, "UPDATE");
    }
  }

  @Override
  public void run()
  {
    try {
      if (isFirstSend) {
        firstSend();
        isFirstSend = false;
      }
    } catch (Exception e) {
      System.err.println(e);
    }

    try {
      update();
    } catch (Exception e) {
      System.err.println(e);
    }
  }

  private void update()
  {
    if (DebugValue.DEBUG_FLAG && DebugValue.DEBUG_PRINT_UPDATE_INTERVAL_FLAG) debugIntervalPrinter.interval();

    Message message = new Message();

    synchronized (Monitor.getInstance().Scheduler.Lock) {
      message.CurrentTime = System.currentTimeMillis();

      // 前回のupdateとのIntervalを計算。初回時は固定時間を信じる
      if (before < 0) {
        message.TimeLength = Monitor.getInstance().Config.UpdateInterval;
      }
      else {
        long diff = message.CurrentTime - before;
        message.TimeLength = diff;
      }
      before = message.CurrentTime;

      for (Map.Entry<Pair<Integer, Long>, Integer> entry : Monitor.getInstance().Scheduler.SampleNumMap.entrySet()) {
        int methodID = entry.getKey().first();
        long threadID = entry.getKey().second();
        double exeRate = (double) entry.getValue() / Monitor.getInstance().Scheduler.Counter;
        double exeTime = exeRate * message.TimeLength;
        ExeTimeInfo info = new ExeTimeInfo(methodID, threadID, exeTime);
        message.ExeTimes.add(info);
      }

      Monitor.getInstance().Scheduler.Counter = 0;
      Monitor.getInstance().Scheduler.SampleNumMap.clear();
    }

    if (!(DebugValue.DEBUG_FLAG && DebugValue.DEBUG_NO_CONNECT)) {
      try {
        Monitor.getInstance().Connector.write(message);
      } catch (IOException e) {
        System.err.println("Connection is closed");
        Monitor.getInstance().Scheduler.Executor.shutdownNow();
      }
    }
  }

  private void firstSend()
  {
    if (DebugValue.DEBUG_FLAG && DebugValue.DEBUG_NO_CONNECT) return;

    Message message = new Message();
    message.CurrentTime = 0;
    message.TimeLength = 0;
    message.Methods.addAll(Monitor.getInstance().StructureDB.IdDataMap.values());
    try {
      Monitor.getInstance().Connector.write(message);
    } catch (IOException e) {
      System.err.println("Connection is closed");
      Monitor.getInstance().Scheduler.Executor.shutdownNow();
    }
  }

}
