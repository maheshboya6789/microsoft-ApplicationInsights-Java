package datadog.trace.instrumentation.servlet3;

import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HttpServlet3Instrumentation extends AbstractServlet3Instrumentation {

  @Override
  public ElementMatcher typeMatcher() {
    return not(isInterface()).and(failSafe(hasSuperType(named("javax.servlet.http.HttpServlet"))));
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("service")
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(isProtected()),
        Servlet3Advice.class.getName());
    return transformers;
  }
}