/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.dependency

import android.view.View
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler

abstract class UniqueViewComponent<T : UniqueViewComponent<T, V>, V : View> :
    ConcreteUniqueComponent<T>(),
    Dependent,
    ManagedHandler by managedHandler() {

    abstract val view: V
}
