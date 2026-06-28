package com.dailyknowledge.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile KnowledgeFileDao _knowledgeFileDao;

  private volatile KnowledgeItemDao _knowledgeItemDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `knowledge_files` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `importTime` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `knowledgeCount` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `knowledge_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileId` INTEGER NOT NULL, `content` TEXT NOT NULL, `indexInFile` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, FOREIGN KEY(`fileId`) REFERENCES `knowledge_files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_items_fileId` ON `knowledge_items` (`fileId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_items_isFavorite` ON `knowledge_items` (`isFavorite`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'fd62a0147e47930bd7deefac9266524c')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `knowledge_files`");
        db.execSQL("DROP TABLE IF EXISTS `knowledge_items`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsKnowledgeFiles = new HashMap<String, TableInfo.Column>(6);
        _columnsKnowledgeFiles.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeFiles.put("fileName", new TableInfo.Column("fileName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeFiles.put("filePath", new TableInfo.Column("filePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeFiles.put("importTime", new TableInfo.Column("importTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeFiles.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeFiles.put("knowledgeCount", new TableInfo.Column("knowledgeCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysKnowledgeFiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesKnowledgeFiles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoKnowledgeFiles = new TableInfo("knowledge_files", _columnsKnowledgeFiles, _foreignKeysKnowledgeFiles, _indicesKnowledgeFiles);
        final TableInfo _existingKnowledgeFiles = TableInfo.read(db, "knowledge_files");
        if (!_infoKnowledgeFiles.equals(_existingKnowledgeFiles)) {
          return new RoomOpenHelper.ValidationResult(false, "knowledge_files(com.dailyknowledge.data.model.KnowledgeFile).\n"
                  + " Expected:\n" + _infoKnowledgeFiles + "\n"
                  + " Found:\n" + _existingKnowledgeFiles);
        }
        final HashMap<String, TableInfo.Column> _columnsKnowledgeItems = new HashMap<String, TableInfo.Column>(5);
        _columnsKnowledgeItems.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeItems.put("fileId", new TableInfo.Column("fileId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeItems.put("content", new TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeItems.put("indexInFile", new TableInfo.Column("indexInFile", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKnowledgeItems.put("isFavorite", new TableInfo.Column("isFavorite", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysKnowledgeItems = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysKnowledgeItems.add(new TableInfo.ForeignKey("knowledge_files", "CASCADE", "NO ACTION", Arrays.asList("fileId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesKnowledgeItems = new HashSet<TableInfo.Index>(2);
        _indicesKnowledgeItems.add(new TableInfo.Index("index_knowledge_items_fileId", false, Arrays.asList("fileId"), Arrays.asList("ASC")));
        _indicesKnowledgeItems.add(new TableInfo.Index("index_knowledge_items_isFavorite", false, Arrays.asList("isFavorite"), Arrays.asList("ASC")));
        final TableInfo _infoKnowledgeItems = new TableInfo("knowledge_items", _columnsKnowledgeItems, _foreignKeysKnowledgeItems, _indicesKnowledgeItems);
        final TableInfo _existingKnowledgeItems = TableInfo.read(db, "knowledge_items");
        if (!_infoKnowledgeItems.equals(_existingKnowledgeItems)) {
          return new RoomOpenHelper.ValidationResult(false, "knowledge_items(com.dailyknowledge.data.model.KnowledgeItem).\n"
                  + " Expected:\n" + _infoKnowledgeItems + "\n"
                  + " Found:\n" + _existingKnowledgeItems);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "fd62a0147e47930bd7deefac9266524c", "32f202f06f349f4917d43f226267ada8");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "knowledge_files","knowledge_items");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `knowledge_files`");
      _db.execSQL("DELETE FROM `knowledge_items`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(KnowledgeFileDao.class, KnowledgeFileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(KnowledgeItemDao.class, KnowledgeItemDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public KnowledgeFileDao knowledgeFileDao() {
    if (_knowledgeFileDao != null) {
      return _knowledgeFileDao;
    } else {
      synchronized(this) {
        if(_knowledgeFileDao == null) {
          _knowledgeFileDao = new KnowledgeFileDao_Impl(this);
        }
        return _knowledgeFileDao;
      }
    }
  }

  @Override
  public KnowledgeItemDao knowledgeItemDao() {
    if (_knowledgeItemDao != null) {
      return _knowledgeItemDao;
    } else {
      synchronized(this) {
        if(_knowledgeItemDao == null) {
          _knowledgeItemDao = new KnowledgeItemDao_Impl(this);
        }
        return _knowledgeItemDao;
      }
    }
  }
}
