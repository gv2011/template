package com.github.gv2011.jstarter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.github.gv2011.util.icol.Opt;

class MainTest {

  private static final String TEST_REF = "com.github.gv2011:template:0.2-SNAPSHOT";

  @Test
  void testGetMainClassName() {
    final Main main = new Main(Opt.empty(), TEST_REF);
    assertThat(main.getMainClassName(), is("com.github.gv2011.template.Main"));
  }

  @Test
  void testGetClassPath() {
    final Main main = new Main(Opt.empty(), TEST_REF);
    Arrays.stream(main.getClassPath()).forEach(System.out::println);
  }

  @Test
  void testRun() {
    final Main main = new Main(Opt.empty(), TEST_REF);
    main.run(new String[]{});
  }

}
