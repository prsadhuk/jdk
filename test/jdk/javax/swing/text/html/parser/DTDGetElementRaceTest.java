/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8382491
 * @summary Verifies DTD.getElement(String) with new name is thread safe
 * @run main DTDGetElementRaceTest
 */

import java.util.Hashtable;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import javax.swing.text.html.parser.DTD;
import javax.swing.text.html.parser.Element;

public final class DTDGetElementRaceTest {
    private static final String ELEMENT_NAME = "race-element";

    public static void main(String[] args) throws Exception {
        DTD dtd = DTD.getDTD("DTDGetElementRaceTest-" + System.nanoTime());

        dtd.elements = new Vector<Element>();
        dtd.elementHash = new RacingHashtable();

        CyclicBarrier start = new CyclicBarrier(3);
        Element[] result = new Element[2];
        Throwable[] failure = new Throwable[2];

        Thread first = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    result[0] = dtd.getElement(ELEMENT_NAME);
                } catch (Throwable t) {
                    failure[0] = t;
                }
            }
        });

        Thread second = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    result[1] = dtd.getElement(ELEMENT_NAME);
                } catch (Throwable t) {
                    failure[1] = t;
                }
            }
        });

        first.start();
        second.start();
        start.await();
        first.join(2000);
        second.join(2000);

        if (first.isAlive() || second.isAlive()) {
            throw new RuntimeException("Timed out waiting for getElement calls");
        }
        if (failure[0] != null) {
            throw new RuntimeException(failure[0]);
        }
        if (failure[1] != null) {
            throw new RuntimeException(failure[1]);
        }

        if (result[0] != result[1]) {
            throw new RuntimeException("getElement returned two distinct Element instances");
        }

        int matches = 0;
        for (Element element : dtd.elements) {
            if (ELEMENT_NAME.equals(element.getName())) {
                matches++;
            }
        }

        if (matches != 1) {
            throw new RuntimeException("Expected exactly one element named "
                    + ELEMENT_NAME + " in DTD.elements, found " + matches);
        }
    }

    private static final class RacingHashtable extends Hashtable<String, Element> {
        private final CountDownLatch bothUnsynchronizedGets = new CountDownLatch(2);

        @Override
        public Element get(Object key) {
            boolean callerAlreadyHoldsTableLock = Thread.holdsLock(this);
            Element element = super.get(key);

            if (!callerAlreadyHoldsTableLock
                    && element == null
                    && ELEMENT_NAME.equals(key)) {
                bothUnsynchronizedGets.countDown();
                try {
                    bothUnsynchronizedGets.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(interrupted);
                }
            }

            return element;
        }
    }
}

