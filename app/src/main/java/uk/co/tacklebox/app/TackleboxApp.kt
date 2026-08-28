package uk.co.tacklebox.app

import android.app.Application
import uk.co.tacklebox.app.data.TackleboxRepository

class TackleboxApp:Application(){ val repository by lazy { TackleboxRepository(this) } }
