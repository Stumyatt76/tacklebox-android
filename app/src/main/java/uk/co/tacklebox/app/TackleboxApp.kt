/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.Application
import uk.co.tacklebox.app.data.TackleboxRepository

class TackleboxApp:Application(){ val repository by lazy { TackleboxRepository(this) } }