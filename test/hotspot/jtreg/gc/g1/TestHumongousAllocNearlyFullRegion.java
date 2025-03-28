/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package gc.g1;

import static gc.testlibrary.Allocation.blackHole;

/*
 * @test TestHumongousAllocNearlyFullRegion
 * @bug 8143587
 * @summary G1: humongous object allocations should work even when there is
 *              not enough space in the G1HeapRegion to fit a filler object.
 * @requires vm.gc.G1
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @library /
 * @run driver gc.g1.TestHumongousAllocNearlyFullRegion
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestHumongousAllocNearlyFullRegion {
    // Heap sizes < 224 MB are increased to 224 MB if vm_page_size == 64K to
    // fulfill alignment constraints.
    private static final int heapSize                       = 224; // MB
    private static final int heapRegionSize                 = 1;   // MB

    public static void main(String[] args) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
            "-XX:+UseG1GC",
            "-Xms" + heapSize + "m",
            "-Xmx" + heapSize + "m",
            "-XX:G1HeapRegionSize=" + heapRegionSize + "m",
            "-Xlog:gc",
            HumongousObjectAllocator.class.getName());

        output.shouldContain("Pause Young (Concurrent Start) (G1 Humongous Allocation)");
        output.shouldHaveExitValue(0);
    }

    static class HumongousObjectAllocator {
        public static void main(String [] args) {
            for (int i = 0; i < heapSize; i++) {
                // 131069 is the number of longs it takes to fill a G1HeapRegion except
                // for 8 bytes on 64 bit.
                blackHole(new long[131069]);
            }
        }
    }
}
