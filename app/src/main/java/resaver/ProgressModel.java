/*
 * Copyright 2016 Mark Fairchild.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package resaver;

/**
 * Android port of the desktop progress model. The Swing implementation is
 * replaced with a plain counter; the app polls {@link #getValue()} and
 * {@link #getMaximum()} from a coroutine.
 *
 * @author Mark Fairchild
 */
final public class ProgressModel {

    public ProgressModel() {
        this(18);
    }

    public ProgressModel(int max) {
        this.MINIMUM = 0;
        this.MAXIMUM = max;
        this.VALUE = 0;
    }

    public ProgressModel(double max) {
        this((int) Math.round(max));
    }

    synchronized public void modifyValue(int delta) {
        this.VALUE = this.VALUE + delta;
    }

    synchronized public void modVSq(double delta) {
        this.modifyValue((int) (Math.sqrt(delta)));
    }

    synchronized public int getValue() {
        return this.VALUE;
    }

    synchronized public int getMaximum() {
        return this.MAXIMUM;
    }

    synchronized public int getMinimum() {
        return this.MINIMUM;
    }

    synchronized public void setValue(int n) {
        this.VALUE = n;
    }

    synchronized public void setMaximum(int n) {
        this.MAXIMUM = n;
    }

    private int VALUE;
    private int MAXIMUM;
    private final int MINIMUM;
}