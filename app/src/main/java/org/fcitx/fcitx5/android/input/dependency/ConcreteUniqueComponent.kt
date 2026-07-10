/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.dependency

import org.mechdancer.dependency.IUniqueComponent
import org.mechdancer.dependency.UniqueComponent
import kotlin.reflect.KClass

/**
 * Input components are final runtime types, so their class is already the scope identity.
 * This avoids generic-supertype reflection while constructing the first input surface.
 */
abstract class ConcreteUniqueComponent<T : ConcreteUniqueComponent<T>> : UniqueComponent<T>() {
    final override val type: KClass<out IUniqueComponent<*>> = javaClass.kotlin
}
