package io.github.rajvinodh.orbit.execution;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import io.github.rajvinodh.orbit.domain.TestResult;
import io.github.rajvinodh.orbit.execution.actions.PostTestAction;
import io.github.rajvinodh.orbit.execution.actions.PreTestAction;
import io.github.rajvinodh.orbit.listener.RecentTestOutcomeProvider;
import java.util.Collection;
import java.util.List;

public class TestExecutionPipeline {

  private final List<PreTestAction> preActions;
  private final List<PostTestAction> postActions;
  private final RetryPolicy retryPolicy;

  public TestExecutionPipeline(
      List<PreTestAction> preActions, List<PostTestAction> postActions, RetryPolicy retryPolicy) {
    this.preActions = preActions;
    this.postActions = postActions;
    this.retryPolicy = retryPolicy;
  }

  public void execute(
      IDevice device,
      TestExecutionContext ctx,
      RemoteAndroidTestRunner runner,
      Collection<ITestRunListener> listeners,
      RecentTestOutcomeProvider outcomeProvider)
      throws Exception {
    int attempt = 0;
    TestResult result;
    do {
      ctx = ctx.withAttemptNumber(attempt);

      for (PreTestAction action : preActions) {
        action.before(device, ctx);
      }

      runner.run(listeners);

      boolean failed = outcomeProvider.didRecentTestFail();
      for (PostTestAction action : postActions) {
        action.after(device, ctx, failed);
      }

      result = outcomeProvider.getLastResult();
      attempt++;
    } while (retryPolicy.shouldRetry(ctx, result));
  }
}
