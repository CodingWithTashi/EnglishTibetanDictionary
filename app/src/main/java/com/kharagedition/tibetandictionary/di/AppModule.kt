package com.kharagedition.tibetandictionary.di
import android.app.Application
import android.content.Context
import androidx.room.Room
import com.kharagedition.tibetandictionary.dao.WordDatabase
import com.kharagedition.tibetandictionary.util.Constant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Singleton
    @Provides
    fun provideDatabase(
        app: Application,
        callback: WordDatabase.Callback
    ) = Room
            .databaseBuilder(app, WordDatabase::class.java, Constant.DATABASE_NAME)
            .createFromAsset(Constant.DATABASE_DIR)
            .addCallback(callback)
            .build()

    @Provides
    fun provideTaskDao(db: WordDatabase) = db.wordDao()

    @ApplicationScope
    @Provides
    @Singleton
    fun provideApplicationScope() = CoroutineScope(SupervisorJob())

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context) = context

}

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope