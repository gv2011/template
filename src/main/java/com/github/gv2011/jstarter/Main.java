package com.github.gv2011.jstarter;

import static com.github.gv2011.util.CollectionUtils.pair;
import static com.github.gv2011.util.Verify.verify;
import static com.github.gv2011.util.ex.Exceptions.call;
import static com.github.gv2011.util.ex.Exceptions.callWithCloseable;
import static com.github.gv2011.util.ex.Exceptions.wrap;
import static java.util.stream.Collectors.joining;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.github.gv2011.util.FileUtils;
import com.github.gv2011.util.Pair;
import com.github.gv2011.util.icol.Opt;
import com.github.gv2011.util.json.JsonFactory;
import com.github.gv2011.util.json.JsonNode;
import com.github.gv2011.util.json.JsonObject;
import com.github.gv2011.util.json.JsonUtils;
import com.github.gv2011.util.m2t.ArtifactRef;
import com.github.gv2011.util.m2t.M2t;
import com.github.gv2011.util.m2t.M2tFactory;
import com.github.gv2011.util.m2t.Scope;


public final class Main{

  @SuppressWarnings("unused")
  private static final Logger LOG = getLogger(Main.class);

  private static final String REF = "com.github.gv2011:jstarter:0.2-SNAPSHOT";

  public static void main(final String[] args) {
    verify(args, a->args.length>=1);
    final Opt<String> home = Opt.ofNullable(System.getenv("JSTARTER_HOME"));
    if(args[0].equals("cp")){
      new Main(home, REF).printClasspath();
    }
    else if(args[0].equals("bash")){
      new Main(home, REF).printBashScript();
    }
    else new Main(home, args[0]).run(tail(args,1));
  }

  private static String[] tail(final String[] args, final int offset) {
    final String[] tail = new String[args.length-offset];
    System.arraycopy(args, offset, tail, 0, tail.length);
    return tail;
  }

  private final Opt<String> home;
  private final ArtifactRef artifact;

  Main(final Opt<String> home, final String artifactRef){
    this.home = home;
    this.artifact = M2tFactory.INSTANCE.get().parse(artifactRef);
	};

  private void printClasspath() {
    try(final M2t m2t = M2tFactory.INSTANCE.get().create()){
      System.out.println(
        m2t.getClasspath(artifact, Scope.RUNTIME).stream().map(Path::toString).collect(joining(":"))
      );
    }
  }

  private void printBashScript() {
    try(final M2t m2t = M2tFactory.INSTANCE.get().create()){
      System.out.println(
        "#!/bin/bash\n"+
        "java -cp "+
        m2t.getClasspath(artifact, Scope.RUNTIME).stream().map(Path::toString).collect(joining(":"))+" "+
        Main.class.getName()+" $@"
      );
    }
  }


  void run(final String[] args){
    final AtomicReference<Throwable> exception = new AtomicReference<>();
    final Thread appThread = new Thread(
      ()->call(()->{
        Thread.currentThread().getContextClassLoader()
        .loadClass(getMainClassName())
        .getMethod("main", String[].class)
        .invoke(null, (Object)args);
      }),
      "app"
    );
    appThread.setContextClassLoader(new URLClassLoader(getClassPath()));
    appThread.setUncaughtExceptionHandler((t,e)->exception.set(e));
    appThread.start();
    call(()->{
      try{appThread.join();}
      finally{
        Optional.ofNullable(exception.get()).ifPresent(t->{throw wrap(t);});
      }
    });
  }

  URL[] getClassPath() {
    return getClassPathAndMainClass().getKey();
  }

  Pair<URL[],String> getClassPathAndMainClass() {
    final JsonFactory jf = JsonUtils.jsonFactory();
    final Opt<Path> path = home.map(h->Paths.get(h).resolve("cache.txt"));

    final Opt<JsonObject> map = path
      .flatMap(p->Files.exists(p) ? Opt.of(p) : Opt.empty())
      .map(p->jf.deserialize(FileUtils.readText(p)).asObject())
    ;
    return map
      .flatMap(m->m.tryGet(artifact.toString()))
      .map(JsonNode::asObject)
      .map(n->{
        final URL[] cp = n.get("cp").asList().stream().map(u->call(()->new URL(u.asString()))).toArray(URL[]::new);
        final String mainClass = n.get("mc").asString();
        return pair(cp, mainClass);
      })
      .orElseGet(()->{
        final URL[] cp;
        final String mainClass;
        try(M2t m2t = M2tFactory.INSTANCE.get().create()){
          cp = m2t
            .getClasspath(artifact, Scope.RUNTIME).stream()
            .map(p->call(()->p.toUri().toURL())).toArray(i->new URL[i])
          ;
          mainClass = getMainClassName(m2t);
        }
        path.ifPresentDo(p->{
          FileUtils.writeText(
            (
              map.stream()
              .flatMap(obj->obj.entrySet().stream())
              .concat(Stream.of(pair(
                artifact.toString(),
                (
                  Stream.<Pair<String,JsonNode>>of(
                    pair("cp", Arrays.stream(cp).map(u->jf.primitive(u.toString())).collect(jf.toJsonList())),
                    pair("mc", jf.primitive(mainClass))
                  )
                  .collect(jf.toJsonObject())
                )
              )))
              .collect(jf.toJsonObject())
              .serialize()
            ),
            p
          );
        });
        return pair(cp, mainClass);
      })
    ;
  }

  String getMainClassName(){
    return getClassPathAndMainClass().getValue();
  }

  String getMainClassName(final M2t m2t){
    m2t.resolve(artifact);
    return callWithCloseable(()->Files.newInputStream(m2t.resolve(artifact)), s->{
      JarInputStream jarStream = new JarInputStream(s);
      Manifest mf = jarStream.getManifest();
      Attributes att = mf.getMainAttributes();
      return (String) att.getOrDefault(new Attributes.Name("Main-Class"), "");
    });
  }

}
