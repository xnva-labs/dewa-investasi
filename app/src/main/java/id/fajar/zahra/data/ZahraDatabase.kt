package id.fajar.zahra.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities=[ProfileEntity::class,MissionEntity::class,RewardEntity::class,PointLedgerEntity::class,ProofEntity::class,AppEventEntity::class,ListEntity::class,ListItemEntity::class],version=6,exportSchema=true)
abstract class ZahraDatabase:RoomDatabase(){
    abstract fun profileDao():ProfileDao
    abstract fun missionDao():MissionDao
    abstract fun rewardDao():RewardDao
    abstract fun pointDao():PointDao
    abstract fun eventDao():EventDao
    abstract fun listDao():ListDao
    abstract fun listItemDao():ListItemDao
    companion object{
        val MIGRATION_4_5=object:Migration(4,5){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE IF NOT EXISTS lists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS list_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, listId INTEGER NOT NULL, title TEXT NOT NULL, checked INTEGER NOT NULL, createdAt INTEGER NOT NULL, checkedAt INTEGER)")
        }}
        val MIGRATION_5_6=object:Migration(5,6){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE point_ledger ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE point_ledger SET sourceKey='LEGACY:' || id WHERE sourceKey='' OR sourceKey IS NULL")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_point_ledger_sourceKey ON point_ledger(sourceKey)")
            db.execSQL("ALTER TABLE events ADD COLUMN bridgeEventId TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_events_bridgeEventId ON events(bridgeEventId)")
        }}
        val MIGRATION_3_4=object:Migration(3,4){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE missions ADD COLUMN proofTarget TEXT NOT NULL DEFAULT ''")}}
        val MIGRATION_2_3=object:Migration(2,3){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("CREATE TABLE IF NOT EXISTS proofs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, missionId INTEGER NOT NULL, proofType TEXT NOT NULL, status TEXT NOT NULL, message TEXT NOT NULL, confidence REAL NOT NULL, photoRetained INTEGER NOT NULL, createdAt INTEGER NOT NULL)")}}
        val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("CREATE TABLE IF NOT EXISTS point_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceType TEXT NOT NULL, sourceId INTEGER, label TEXT NOT NULL, points INTEGER NOT NULL, createdAt INTEGER NOT NULL)");db.execSQL("ALTER TABLE rewards ADD COLUMN unlockedAt INTEGER");db.execSQL("ALTER TABLE rewards ADD COLUMN claimedAt INTEGER")}}
        @Volatile private var INSTANCE:ZahraDatabase?=null
        fun get(context:Context):ZahraDatabase=INSTANCE?: synchronized(this){INSTANCE?:Room.databaseBuilder(context.applicationContext,ZahraDatabase::class.java,"zahra.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6).build().also{INSTANCE=it}}
    }
}
