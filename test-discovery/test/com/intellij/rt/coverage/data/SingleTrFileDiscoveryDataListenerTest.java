/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.rt.coverage.data;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleTrFileDiscoveryDataListenerTest {

  public static final byte[] EMPTY = {0x1, 0x1, 0x3,
      0x00 //count
      // Start of dictionary:
      , 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03};
  public static final byte[] NO_TESTS_ONE_NAME = {0x1, 0x1, 0x3,
      0x1, // count
      0x1, // 1 - ABC
      0x3, 0x41, 0x42, 0x43 // "ABC"
      // Start of dictionary:
      , 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03};
  public static final byte[] SINGLE_TEST_NO_METHODS = {0x1, 0x1,
      0x2, // TestMarker
      0x1, 0x1, // test ABC.ABC
      0x0, //no classes
      0x3, // DictionaryMarker
      0x1, // count
      0x1, // 1 - ABC
      0x3, 0x41, 0x42, 0x43 // "ABC"
      // Link to start of dictionary:
      , 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07};
  public static final byte[] SINGLE_TEST_SINGLE_METHOD = {0x1, 0x1,
      0x2, // TestMarker
      0x1, 0x2, // test A.B
      0x1, // 1 class
      0x2, // Class A
      0x1, // 1 method
      0x3, // method C
      0x3, // DictionaryMarker
      0x3, // count
      0x3, // 3 - C
      0x1, 0x43, // "C"
      0x2, // 2 - B
      0x1, 0x42, // "B"
      0x1, // 1 - A
      0x1, 0x41 // "A"
      // Link to start of dictionary:
      , 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0A};


  public static final byte[] V2_EMPTY = {0x1, 0x2,
  };
  public static final byte[] V2_NO_TESTS_ONE_NAME = {0x1, 0x2,
      0x4, // Partial directory
      0x1, // count
      0x1, // 1 - A
      0x3, 0x41, 0x42, 0x43 // "ABC"
  };
  public static final byte[] V2_SINGLE_TEST_SINGLE_METHOD = {0x1, 0x2,
      0x4, // Partial directory
      0x2, // count
      0x1, // 1 - A
      0x1, 0x41, // "A"
      0x2, // 2 - B
      0x1, 0x42, // "B"
      0x2, // TestMarker
      0x1, 0x2, // test A.B
      0x1, // 1 class
      0x1, // Class A
      0x1, // 1 method
      0x2, // method B
  };
  public static final byte[] V2_TWO_TESTS_INCREMENTAL_DICT = {0x1, 0x2,
      0x4, // Partial directory
      0x2, // count
      0x1, // 1 - A
      0x1, 0x41, // "A"
      0x2, // 1 - B
      0x1, 0x42, // "B"
      0x2, // TestMarker
      0x1, 0x2, // test A.B
      0x1, // 1 class
      0x1, // Class A
      0x1, // 1 method
      0x1, // method A
      0x2, // TestMarker
      0x2, 0x1, // test B.A
      0x1, // 1 class
      0x1, // Class A
      0x1, // 1 method
      0x1, // method A
  };

  private static final byte V1 = 0x1;
  private static final byte V2 = 0x2;

  @Test
  public void testNoCoverage() throws Exception {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final LongDataOutputStream dos = new LongDataOutputStream(baos);
    final SingleTrFileDiscoveryDataListener listener = new SingleTrFileDiscoveryDataListener(dos);
    listener.testsFinished();
    assertThat(baos.toByteArray()).isEqualTo(EMPTY);
  }

  @Test
  public void testOnlyEnumerator() throws Exception {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final LongDataOutputStream dos = new LongDataOutputStream(baos);
    final SingleTrFileDiscoveryDataListener listener = new SingleTrFileDiscoveryDataListener(dos);
    listener.getIncrementalNameEnumerator().enumerate("ABC");
    listener.testsFinished();
    assertThat(baos.toByteArray()).isEqualTo(NO_TESTS_ONE_NAME);
  }

  @Test
  public void testSingleTestNothingVisited() throws Exception {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final LongDataOutputStream dos = new LongDataOutputStream(baos);
    final SingleTrFileDiscoveryDataListener listener = new SingleTrFileDiscoveryDataListener(dos);
    final String name = "ABC";
    listener.getIncrementalNameEnumerator().enumerate(name);
    final Map<Integer, boolean[]> classes = new HashMap<Integer, boolean[]>();
    final Map<Integer, int[]> methods = new HashMap<Integer, int[]>();
    classes.put(1, new boolean[]{false});
    methods.put(1, new int[]{1});
    listener.testFinished(name, name, classes, methods);
    listener.testsFinished();
    assertThat(baos.toByteArray()).isEqualTo(SINGLE_TEST_NO_METHODS);
  }

  @Test
  public void testSingleTestOnlyOneClassVisited() throws Exception {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final LongDataOutputStream dos = new LongDataOutputStream(baos);
    final SingleTrFileDiscoveryDataListener listener = new SingleTrFileDiscoveryDataListener(dos);

    listener.getIncrementalNameEnumerator().enumerate("A");
    listener.getIncrementalNameEnumerator().enumerate("B");
    listener.getIncrementalNameEnumerator().enumerate("C");

    final Map<Integer, boolean[]> classes = new HashMap<Integer, boolean[]>();
    final Map<Integer, int[]> methods = new HashMap<Integer, int[]>();
    classes.put(2, new boolean[]{true});
    classes.put(3, new boolean[]{false});
    methods.put(2, new int[]{3});
    methods.put(3, new int[]{2});
    listener.testFinished("A", "B", classes, methods);
    listener.testsFinished();
    assertThat(baos.toByteArray()).isEqualTo(SINGLE_TEST_SINGLE_METHOD);
  }


  @Test
  public void testV2NoCoverage() throws Exception {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final LongDataOutputStream dos = new LongDataOutputStream(baos);
    final SingleTrFileDiscoveryDataListener listener = new SingleTrFileDiscoveryDataListener(dos, V2);
    listener.testsFinished();
    assertThat(baos.toByteArray()).isEqualTo(V2_EMPTY);
  }


  @Test
  public void testV2OnlyEnumerator() throws Exception {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final LongDataOutputStream dos = new LongDataOutputStream(baos);
    final SingleTrFileDiscoveryDataListener listener = new SingleTrFileDiscoveryDataListener(dos, V2);
    listener.getIncrementalNameEnumerator().enumerate("ABC");
    listener.testsFinished();
    assertThat(baos.toByteArray()).isEqualTo(V2_NO_TESTS_ONE_NAME);
  }

  @Test
  public void testV2SingleTestOnlyOneClassVisited() throws Exception {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final LongDataOutputStream dos = new LongDataOutputStream(baos);
    final SingleTrFileDiscoveryDataListener listener = new SingleTrFileDiscoveryDataListener(dos, V2);

    final String name1 = "A";
    final String name2 = "B";
    listener.getIncrementalNameEnumerator().enumerate(name1);
    listener.getIncrementalNameEnumerator().enumerate(name2);

    final Map<Integer, boolean[]> classes = new HashMap<Integer, boolean[]>();
    final Map<Integer, int[]> methods = new HashMap<Integer, int[]>();
    classes.put(1, new boolean[]{true});
    classes.put(2, new boolean[]{false});
    methods.put(1, new int[]{2});
    methods.put(2, new int[]{1});
    listener.testFinished(name1, name2, classes, methods);
    listener.testsFinished();
    assertThat(baos.toByteArray()).isEqualTo(V2_SINGLE_TEST_SINGLE_METHOD);
  }

  @Test
  public void testV2TwoTestsIncrementalDict() throws Exception {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final LongDataOutputStream dos = new LongDataOutputStream(baos);
    final SingleTrFileDiscoveryDataListener listener = new SingleTrFileDiscoveryDataListener(dos, V2);

    listener.getIncrementalNameEnumerator().enumerate("A");

    final Map<Integer, boolean[]> classes = new HashMap<Integer, boolean[]>();
    final Map<Integer, int[]> methods = new HashMap<Integer, int[]>();
    classes.put(1, new boolean[]{true});
    methods.put(1, new int[]{1});

    listener.testFinished("A", "B", classes, methods);
    listener.testFinished("B", "A", classes, methods);


    listener.testsFinished();
    assertThat(baos.toByteArray()).isEqualTo(V2_TWO_TESTS_INCREMENTAL_DICT);
  }
}