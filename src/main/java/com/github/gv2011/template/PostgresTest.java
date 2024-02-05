package com.github.gv2011.template;

import static org.slf4j.LoggerFactory.getLogger;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Random;

import org.slf4j.Logger;

import com.github.gv2011.util.jdbc.JdbcUtils;

public class PostgresTest {

  private static final int CHUNK = 1000;

  private static final Logger LOG = getLogger(Main.class);

  private static final Encoder B64 = Base64.getEncoder();

  public static void main(final String[] args) throws SQLException {
    try(Connection cn = DriverManager.getConnection("jdbc:postgresql://localhost/", "postgres", "M6lfn5ND7nfz2ophA0G3")){
      cn.setAutoCommit(false);
      try(Statement stmt = cn.createStatement()){
        stmt.setFetchSize(100000);
        try(final ResultSet rs = stmt.executeQuery("select id from test")){
          if(rs.next()){
            boolean run = true;
            int i=0;
            final Instant startAll = Instant.now();
            Instant start = startAll;
            Instant last = startAll;
            long total = 0;
            long sum = 0L;
            final long textTotal = 0L;
            while(run){
              sum += rs.getLong(1);
//              textTotal += rs.getString(2).length();
              if(i==CHUNK){
                last = Instant.now();
                LOG.info("{}", Duration.between(start, last).dividedBy(CHUNK).getNano());
                LOG.trace("{}", sum);
                i=0;
                start = last;
                total += CHUNK;
              }
              i++;
              run = rs.next();
            }
            total += i;
            final Duration totalTime = Duration.between(startAll, last);
            LOG.info(
              "{} total, {} textTotal, {} time total, medium {}, medium tex {}",
              total, textTotal, totalTime,
              totalTime.dividedBy(total).getNano(),
              textTotal/total
            );
          }
        }
      }
    }
  }

  private static void create() throws SQLException {
    final Random r = new Random(new SecureRandom().nextLong());
    try(Connection cn = DriverManager.getConnection("jdbc:postgresql://localhost/", "postgres", "M6lfn5ND7nfz2ophA0G3")){
      //JdbcUtils.execute(cn, "drop table test");
      //JdbcUtils.execute(cn, "create table test(id bigint not null, value text, primary key (id))");
      long count = JdbcUtils.executeSingle(cn, "select count(id) from test", rs->rs.getLong(1));
      LOG.info(
        "Count: {}",
        count
      );
      cn.setAutoCommit(false);
      try(PreparedStatement stmt = cn.prepareStatement("INSERT INTO test (id, value) VALUES (?,?)")){
        while(count<10000000){
          stmt.setLong(1, r.nextLong());
          stmt.setString(2, string(r));
          stmt.execute();
          count++;
        }
      }
      cn.commit();
    }
  }

  private static String string(final Random r) {
    final byte[] bytes = new byte[r.nextInt(511)];
    r.nextBytes(bytes);
    return B64.encodeToString(bytes);
  }


}
