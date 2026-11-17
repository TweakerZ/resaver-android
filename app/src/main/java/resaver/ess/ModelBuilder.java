/*
 * Copyright 2018 Mark.
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
package resaver.ess;

import java.util.List;
import java.util.Objects;
import resaver.ProgressModel;

/**
 * Android port of the desktop model builder.
 *
 * The desktop class builds the Swing tree that ReSaver shows on the left of
 * its window. The Android app does not use a tree: it reads the counts and
 * names straight from the save after parsing. Every hook is therefore a no-op
 * and the class only exists because the parser calls it as it walks the file.
 *
 * @author Mark
 */
public class ModelBuilder {

    public ModelBuilder(ProgressModel progress) {
        Objects.requireNonNull(progress);
        this.PROGRESS = progress;
    }

    public void addPluginInfo(PluginInfo plugins) {
        this.PROGRESS.modifyValue(1);
    }

    public void addGlobalVariableTable(GlobalVariableTable gvt) {
    }

    public void addStringTable(resaver.ess.papyrus.StringTable table) {
    }

    public void addScripts(resaver.ess.papyrus.ScriptMap scripts) {
    }

    public void addStructs(resaver.ess.papyrus.StructMap structs) {
    }

    public void addReferences(resaver.ess.papyrus.ReferenceMap references) {
    }

    public void addArrays(resaver.ess.papyrus.ArrayMap arrays) {
    }

    public void addUnbinds(resaver.ess.papyrus.UnbindList unbinds) {
    }

    public void addUnknownIDList(List<resaver.ess.papyrus.EID> unknownIDs) {
    }

    public void addAnimations(AnimObjects animations) {
    }

    public void addScriptInstances(resaver.ess.papyrus.ScriptInstanceMap instances) {
    }

    public void addStructInstances(resaver.ess.papyrus.StructInstanceMap instances) {
    }

    public void addThreads(resaver.ess.papyrus.ActiveScriptMap threads) {
    }

    public void addFunctionMessages(List<resaver.ess.papyrus.FunctionMessage> messages) {
    }

    public void addSuspendedStacks(resaver.ess.papyrus.SuspendedStackMap stacks) {
    }

    public void addSuspendedStacks1(resaver.ess.papyrus.SuspendedStackMap stacks) {
    }

    public void addSuspendedStacks2(resaver.ess.papyrus.SuspendedStackMap stacks) {
    }

    public void addChangeForms(ChangeFormCollection changeForms) {
        this.PROGRESS.modifyValue(1);
    }

    public void addOtherData(resaver.ess.papyrus.OtherData data) {
    }

    public void addWarning(String warning) {
    }

    public void finish() {
    }

    public Object finish(ESS ess) {
        this.PROGRESS.setValue(this.PROGRESS.getMaximum());
        return this;
    }

    final private ProgressModel PROGRESS;
}