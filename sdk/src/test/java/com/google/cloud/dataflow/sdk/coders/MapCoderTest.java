/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.coders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link MapCoder}. */
@RunWith(JUnit4.class)
public class MapCoderTest {
  @Test
  public void testGetInstanceComponentsNonempty() {
    Map<Integer, String> map = new HashMap<>();
    map.put(17, "foozle");
    List<Object> components = MapCoder.getInstanceComponents(map);
    assertEquals(2, components.size());
    assertEquals(17, components.get(0));
    assertEquals("foozle", components.get(1));
  }

  @Test
  public void testGetInstanceComponentsEmpty() {
    Map<Integer, String> map = new HashMap<>();
    List<Object> components = MapCoder.getInstanceComponents(map);
    assertNull(components);
  }
}
