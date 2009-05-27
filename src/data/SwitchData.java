package com.intellij.rt.coverage.data;

import com.intellij.rt.coverage.util.CoverageIOUtil;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class SwitchData implements CoverageData {
  private int[] myKeys;

  private int myDefaultHits;
  private int[] myHits;

  public SwitchData(final int[] keys) {

    myKeys = keys;

    myHits = new int[keys.length];
    Arrays.fill(myHits, 0);

  }

  public void touch(final int key) {
    if (key == -1) {
      myDefaultHits++;
    }
    else if (key < myHits.length && key >= 0) {
      myHits[key]++;
    }
  }

  public int getDefaultHits() {
    return myDefaultHits;
  }

  public int[] getHits() {
    return myHits;
  }

  public void save(final DataOutputStream os) throws IOException {
    CoverageIOUtil.writeINT(os, myDefaultHits);
    CoverageIOUtil.writeINT(os, myHits.length);
    for (int i = 0; i < myHits.length; i++) {
      CoverageIOUtil.writeINT(os, myKeys[i]);
      CoverageIOUtil.writeINT(os, myHits[i]);
    }
  }

  public void merge(final CoverageData data) {
    SwitchData switchData = (SwitchData)data;
    myDefaultHits += switchData.myDefaultHits;
    for (int i = Math.min(myHits.length, switchData.myHits.length) - 1; i >= 0; i--) {
      myHits[i] += switchData.myHits[i];
    }
    if (switchData.myHits.length > myHits.length) {
      int[] old = myHits;
      myHits = new int[switchData.myHits.length];
      System.arraycopy(old, 0, myHits, 0, old.length);
      System.arraycopy(switchData.myHits, old.length, myHits, old.length, myHits.length - old.length);
    }
    if ((myKeys.length == 0) && (switchData.myKeys.length > 0)) {
      myKeys = switchData.myKeys;
    }
  }

  public void setDefaultHits(final int defaultHits) {
    myDefaultHits = defaultHits;
  }

  public void setKeysAndHits(final int[] keys, final int[] hits) {
    myKeys = keys;
    myHits = hits;
  }

  public int[] getKeys() {
    return myKeys;
  }
}
