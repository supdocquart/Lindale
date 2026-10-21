package androidx.hilt.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metrolist.music.utils.LocalDatabase
import com.metrolist.music.utils.LocalSyncUtils
import com.metrolist.music.utils.LocalDownloadUtil
import com.metrolist.music.viewmodels.MetrolistViewModelFactory

@Composable
inline fun <reified VM : ViewModel> hiltViewModel(): VM {
    val db = LocalDatabase.current
    val sync = LocalSyncUtils.current
    val dl = LocalDownloadUtil.current
    val factory = remember(db, sync, dl) {
        MetrolistViewModelFactory(database = db, syncUtils = sync, downloadUtil = dl)
    }
    return viewModel<VM>(factory = factory)
}
