/*
 * Copyright 2015-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.central;

import javax.annotation.Nullable;

// this class is designed to be used with apache commons daemon
// http://commons.apache.org/proper/commons-daemon/procrun.html
public class Bootstrap {

    private static volatile @Nullable CentralModule centralModule;

    private Bootstrap() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("start")) {
            centralModule = new CentralModule();
        } else if (args[0].equals("stop")) {
            if (centralModule != null) {
                centralModule.shutdown();
            }
        } else {
            throw new IllegalStateException("Unexpected arg: " + args[0]);
        }
    }
}
