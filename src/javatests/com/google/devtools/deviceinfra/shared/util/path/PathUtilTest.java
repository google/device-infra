/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.deviceinfra.shared.util.path;

import junit.framework.TestCase;

/** Unit tests for the {@link PathUtil} class. */
public class PathUtilTest extends TestCase {

  public void testJoin() throws Exception {
    assertEquals("", PathUtil.join());
    assertEquals("/a", PathUtil.join("/a"));
    assertEquals("a", PathUtil.join("a"));
    assertEquals("a/b/c", PathUtil.join("a", "b", "c"));
    assertEquals("a/c", PathUtil.join("a", "", "c"));
    assertEquals("a", PathUtil.join("a", "", ""));
    assertEquals("a/ ", PathUtil.join("a", "", " "));
    assertEquals("a/ / ", PathUtil.join("a", " ", " "));
    assertEquals(" /a/b/c/ /e/", PathUtil.join(" ", "a", "b", "c", " ", "e/"));
    assertEquals("/foo/bar", PathUtil.join("/", "foo", "bar"));
    assertEquals("/foo/bar", PathUtil.join("//", "foo", "bar"));
    assertEquals("/foo/bar", PathUtil.join("//", "/foo/", "/bar"));
    assertEquals("/foo/bar/", PathUtil.join("//", "/foo/", "/bar/"));
    assertEquals("/foo/bar/", PathUtil.join("//", "foo", "bar/"));
    assertEquals("/bar", PathUtil.join("//", "//", "bar"));
    assertEquals("/foo/bar", PathUtil.join("/foo/", "bar"));
    assertEquals("/alpha/beta/gamma", PathUtil.join("/alpha", "/beta", "gamma"));
    assertEquals("/alpha/beta/gamma", PathUtil.join("/alpha", "/beta/", "gamma"));
    assertEquals("/alpha/beta/gamma", PathUtil.join("/alpha/", "/beta/", "gamma"));
    assertEquals("", PathUtil.join("", ""));
    assertEquals(" ", PathUtil.join(" "));
    assertEquals(" / ", PathUtil.join(" ", " "));
    assertEquals(" / / / ", PathUtil.join(" ", " ", " ", " "));
    assertEquals(" / / / /a", PathUtil.join(" ", " ", " ", " ", "a"));
    assertEquals(" / / / /a/", PathUtil.join(" ", " ", " ", " ", "a/"));
    assertEquals("foo/", PathUtil.join("", "foo/"));
    assertEquals("foo", PathUtil.join("", "foo"));
    assertEquals("/foo", PathUtil.join("", "/foo"));
    assertEquals("/foo/bar", PathUtil.join("", "/foo/bar"));
    assertEquals("/foo/bar", PathUtil.join("", "", "/foo/bar"));
    assertEquals("/foo/bar", PathUtil.join("", "//foo/bar"));
    assertEquals("foo/bar", PathUtil.join("", "foo/bar"));
    assertEquals(" foo/bar", PathUtil.join("", " foo/bar"));
    assertEquals(" foo/ bar", PathUtil.join("", " foo/ bar"));
    assertEquals(" foo/ bar", PathUtil.join("", " foo/ bar", ""));
    assertEquals(" foo/ bar", PathUtil.join("", "", " foo/ bar", ""));

    try {
      PathUtil.join((String) null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  public void testBasename() throws Exception {
    assertEquals("", PathUtil.basename(""));
    assertEquals("", PathUtil.basename("/"));
    assertEquals("bar", PathUtil.basename("bar"));
    assertEquals("bar", PathUtil.basename("bar///"));
    assertEquals("bar", PathUtil.basename("/bar"));
    assertEquals("bar", PathUtil.basename("/bar/"));
    assertEquals("bar", PathUtil.basename("/bar///"));
    assertEquals("bar", PathUtil.basename("///bar///"));
    assertEquals("bar", PathUtil.basename("/foo/bar"));
    assertEquals("bar", PathUtil.basename("/foo/bar/"));
    assertEquals("bar", PathUtil.basename("//foo//bar//"));
    assertEquals("bar", PathUtil.basename("//foo//baz//bar//"));
  }

  public void testDirname() throws Exception {
    assertEquals(".", PathUtil.dirname(""));
    assertEquals("/", PathUtil.dirname("/"));
    assertEquals("/", PathUtil.dirname("/etc"));
    assertEquals("/", PathUtil.dirname("/etc/"));
    assertEquals("/etc", PathUtil.dirname("/etc/passwd"));
    assertEquals("/etc", PathUtil.dirname("/etc/passwd/"));
    assertEquals("/etc", PathUtil.dirname("/etc/passwd///"));
    assertEquals("/etc", PathUtil.dirname("///etc/passwd///"));
    assertEquals("/etc", PathUtil.dirname("///etc///passwd///"));
    assertEquals("/usr/local", PathUtil.dirname("/usr/local/bin"));
    assertEquals("/usr/local/bin", PathUtil.dirname("/usr/local/bin//binary"));
    assertEquals(".", PathUtil.dirname("passwd"));
  }

  public void testMakeRelative() throws Exception {
    assertEquals("simba", PathUtil.makeRelative("/gfs/bp/pso", "/gfs/bp/pso/simba"));
    assertEquals("simba", PathUtil.makeRelative("/gfs/bp/pso", "/gfs/bp/pso/simba/"));
    assertEquals("simba", PathUtil.makeRelative("/gfs/bp/pso/", "/gfs/bp/pso/simba"));
    assertEquals("simba", PathUtil.makeRelative("/gfs/bp/pso/", "/gfs/bp/pso/simba/"));
    assertEquals("simba/eeyore", PathUtil.makeRelative("/gfs/bp/pso/", "/gfs/bp/pso/simba/eeyore"));
    assertEquals(
        "simba/eeyore", PathUtil.makeRelative("/gfs/bp/pso/", "/gfs/bp/pso/simba/eeyore/"));
    assertEquals("", PathUtil.makeRelative("/gfs/bp/pso/", "/gfs/bp/pso"));
    assertEquals("", PathUtil.makeRelative("/gfs/bp/pso", "/gfs/bp/pso/"));
    assertEquals("", PathUtil.makeRelative("/gfs/bp/pso", "/gfs/bp/pso///"));
    assertEquals("", PathUtil.makeRelative("/gfs/bp/pso", "//gfs//bp//pso///"));
    assertEquals("already_relative", PathUtil.makeRelative("/gfs/bp/pso", "already_relative"));
    assertEquals("already/relative", PathUtil.makeRelative("/gfs/bp/pso", "already/relative"));
    assertEquals("already/relative", PathUtil.makeRelative("/gfs/bp/pso", "already/relative/"));
    assertEquals("already/relative", PathUtil.makeRelative("/gfs/bp/pso", "/already/relative/"));
    assertEquals("already/relative", PathUtil.makeRelative("/gfs/bp/pso", "../already/relative/"));
    assertEquals(
        "already/relative", PathUtil.makeRelative("/gfs/bp/pso", "../already/../relative/"));
    assertEquals(
        "already/relative", PathUtil.makeRelative("/gfs/bp/pso", "../already//relative/.."));
    assertEquals(
        "already/relative", PathUtil.makeRelative("/gfs/bp/pso", "../already//relative/../"));
    assertEquals(
        "already/relative", PathUtil.makeRelative("/gfs/bp/pso", "../already//relative/../////"));
    assertEquals(
        "already/relative",
        PathUtil.makeRelative("/gfs/bp/pso", "///../already//relative/../////"));
    assertEquals("gfs/bp/psxfoo", PathUtil.makeRelative("/gfs/bp/pso/", "/gfs/bp/psxfoo/"));
    assertEquals("foo", PathUtil.makeRelative("/gfs/bp/pso/", "/gfs/bp/psofoo/"));
    assertEquals(
        "../../../path/to/cookie",
        PathUtil.makeRelative("/gfs/bp/pso", "..../../....../../....../../..path/to/cookie"));
  }
}
