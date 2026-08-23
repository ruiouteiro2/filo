package com.filo.app

import android.app.Application
import kotlinx.coroutines.launch
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.filo.app.core.prefs.FiloPrefs
import com.filo.app.data.FiloRepository
import okhttp3.OkHttpClient

class FiloApp : Application(), ImageLoaderFactory {

    lateinit var repository: FiloRepository
        private set

    lateinit var prefs: FiloPrefs
        private set

    /** Lives as long as the process, which is the point: widgets outlive the activity. */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    override fun onCreate() {
        super.onCreate()
        prefs = FiloPrefs(this)
        repository = FiloRepository(this)

        // Bound here rather than in the ViewModel: the ViewModel's scope dies with the
        // screen, and a realtime change arriving after that used to update nothing at all.
        repository.onRemoteChange = {
            appScope.launch {
                runCatching {
                    com.filo.app.work.writeSnapshot(this@FiloApp, repository, prefs)
                    com.filo.app.widget.WidgetUpdater.updateAll(this@FiloApp)
                }
            }
        }
    }

    /**
     * Every image Coil loads goes out with a real User-Agent. The map tiles come from OSM /
     * CARTO, whose usage policies require one, and tile CDNs quietly refuse anonymous
     * clients - which looks like "the map is not working" with nothing in the logs.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Filo/" + BuildConfig.VERSION_NAME + " (Android)")
                            .build(),
                    )
                }
                .build()
        }
        .crossfade(true)
        .build()
}
