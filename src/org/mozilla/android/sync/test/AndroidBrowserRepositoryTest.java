/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.test;

import org.mozilla.android.sync.MainActivity;
import org.mozilla.android.sync.repositories.InactiveSessionException;
import org.mozilla.android.sync.repositories.RepositorySession;
import org.mozilla.android.sync.repositories.Utils;
import org.mozilla.android.sync.repositories.android.AndroidBrowserRepository;
import org.mozilla.android.sync.repositories.android.AndroidBrowserRepositoryDatabaseHelper;
import org.mozilla.android.sync.repositories.android.AndroidBrowserRepositorySession;
import org.mozilla.android.sync.repositories.delegates.RepositorySessionFetchRecordsDelegate;
import org.mozilla.android.sync.repositories.delegates.RepositorySessionGuidsSinceDelegate;
import org.mozilla.android.sync.repositories.delegates.RepositorySessionWipeDelegate;
import org.mozilla.android.sync.repositories.domain.Record;
import org.mozilla.android.sync.test.helpers.DefaultFetchDelegate;
import org.mozilla.android.sync.test.helpers.DefaultSessionCreationDelegate;
import org.mozilla.android.sync.test.helpers.DefaultStoreDelegate;
import org.mozilla.android.sync.test.helpers.ExpectBeginDelegate;
import org.mozilla.android.sync.test.helpers.ExpectBeginFailDelegate;
import org.mozilla.android.sync.test.helpers.ExpectFetchDelegate;
import org.mozilla.android.sync.test.helpers.ExpectFetchSinceDelegate;
import org.mozilla.android.sync.test.helpers.ExpectFinishDelegate;
import org.mozilla.android.sync.test.helpers.ExpectFinishFailDelegate;
import org.mozilla.android.sync.test.helpers.ExpectGuidsSinceDelegate;
import org.mozilla.android.sync.test.helpers.ExpectInvalidRequestFetchDelegate;
import org.mozilla.android.sync.test.helpers.ExpectStoredDelegate;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

public abstract class AndroidBrowserRepositoryTest extends ActivityInstrumentationTestCase2<MainActivity> {
  
  protected AndroidBrowserRepositoryDatabaseHelper helper;
  
  public AndroidBrowserRepositoryTest() {
    super(MainActivity.class);
  }

  public Context getApplicationContext() {
    return this.getInstrumentation().getTargetContext().getApplicationContext();
  }

  protected void performWait(Runnable runnable) throws AssertionError {
    AndroidBrowserRepositoryTestHelper.testWaiter.performWait(runnable);
  }
  
  protected void performNotify() {
    AndroidBrowserRepositoryTestHelper.testWaiter.performNotify();
  }

  protected AndroidBrowserRepositorySession getSession() {
    return AndroidBrowserRepositoryTestHelper.session;
  }
  
  public void tearDown() {
    if (helper != null) {
      helper.close();
    }
  }
  
  private void wipe() {
    if (helper == null) {
      helper = getDatabaseHelper();
    }
    helper.wipe();
  }

  public void setUp() {
    Log.i("rnewman", "Wiping.");
    wipe();
  }
  
  protected void prepSession() {
    AndroidBrowserRepositoryTestHelper.prepareRepositorySession(getApplicationContext(),
        new SetupDelegate(), true, getRepository());
    // Clear old data.
    wipe();
  }
  
  protected void prepEmptySession() {
    AndroidBrowserRepositoryTestHelper.prepEmptySession(getApplicationContext(), getRepository());
  }
  
  protected void prepEmptySessionWithoutBegin() {
    AndroidBrowserRepositoryTestHelper.prepEmptySessionWithoutBegin(getApplicationContext(), getRepository());
  }
  
  protected Runnable getStoreRunnable(final Record record, final ExpectStoredDelegate delegate) {
    return new Runnable() {
      public void run() {
        getSession().store(record, delegate);
      }
    };
  }
  
  protected Runnable getFetchAllRunnable(final ExpectFetchDelegate delegate) {
    return new Runnable() {
      public void run() {
        getSession().fetchAll(delegate);
      }
    };
  }
  
  public static Runnable storeRunnable(final RepositorySession session, final Record record) {
    return new Runnable() {
      @Override
      public void run() {
        session.store(record, new ExpectStoredDelegate(record.guid));
      }
    };
  }

  public static Runnable fetchAllRunnable(final RepositorySession session, final ExpectFetchDelegate delegate) {
    return new Runnable() {
      @Override
      public void run() {
        session.fetchAll(delegate);        
      }
    };
  }
  public static Runnable fetchAllRunnable(final RepositorySession session, final String[] expectedGUIDs) {
    return fetchAllRunnable(session, new ExpectFetchDelegate(expectedGUIDs));
  }
  
  public static Runnable guidsSinceRunnable(final RepositorySession session, final long timestamp, final String[] expected) {
    return new Runnable() {
      @Override
      public void run() {
        session.guidsSince(timestamp, new ExpectGuidsSinceDelegate(expected));
      }
    };
  }  
  public static Runnable fetchSinceRunnable(final RepositorySession session, final long timestamp, final String[] expected) {
    return new Runnable() {
      @Override
      public void run() {
        session.fetchSince(timestamp, new ExpectFetchSinceDelegate(timestamp, expected));
      }
    };
  }
  public static Runnable fetchRunnable(final RepositorySession session, final String[] guids) {
    return fetchRunnable(session, guids, guids);
  }
  public static Runnable fetchRunnable(final RepositorySession session, final String[] guids, final DefaultFetchDelegate delegate) {
    return new Runnable() {
      @Override
      public void run() {
        session.fetch(guids, delegate);
      }
    };    
  }
  public static Runnable fetchRunnable(final RepositorySession session, final String[] guids, final String[] expected) {
    return fetchRunnable(session, guids, new ExpectFetchDelegate(expected));
  }

  protected abstract AndroidBrowserRepository getRepository();
  protected abstract AndroidBrowserRepositoryDatabaseHelper getDatabaseHelper();
  protected abstract void verifyExpectedRecordReturned(Record expected, Record actual);
  
  // Tests to implement
  public abstract void testFetchAll();
  public abstract void testGuidsSinceReturnMultipleRecords();
  public abstract void testGuidsSinceReturnNoRecords();
  public abstract void testFetchSinceOneRecord();
  public abstract void testFetchSinceReturnNoRecords();
  public abstract void testFetchOneRecordByGuid();
  public abstract void testFetchMultipleRecordsByGuids();
  public abstract void testFetchNoRecordByGuid();
  public abstract void testWipe();
  public abstract void testStore();
  public abstract void testRemoteNewerTimeStamp();
  public abstract void testLocalNewerTimeStamp();
  public abstract void testDeleteRemoteNewer();
  public abstract void testDeleteLocalNewere();
  public abstract void testDeleteRemoteLocalNonexistent();
  
  /*
   * Test abstractions
   */
  protected void basicStoreTest(Record record) {
    prepSession();    
    performWait(storeRunnable(getSession(), record));
  }
  
  protected void basicFetchAllTest(Record[] expected) {
    Log.i("rnewman", "Starting testFetchAll.");
    AndroidBrowserRepositoryTestHelper.prepareRepositorySession(getApplicationContext(),
        new SetupDelegate(), true, getRepository());
    Log.i("rnewman", "Prepared.");
    String[] expectedGUIDs = new String[expected.length];
    for (int i = 0; i < expectedGUIDs.length; i++) {
      expectedGUIDs[i] = expected[i].guid;
    }

    AndroidBrowserRepositorySession session = getSession();
    for (Record record : expected) {
      performWait(storeRunnable(session, record)); 
    }
    performWait(fetchAllRunnable(session, expectedGUIDs));
  }
  
  /*
   * Tests for guidsSince
   */
  protected void guidsSinceReturnMultipleRecords(Record record0, Record record1) {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    long timestamp = System.currentTimeMillis();

    String[] expected = new String[2];
    expected[0] = record0.guid;
    expected[1] = record1.guid;
    record0.lastModified = timestamp + 1000;
    record1.lastModified = timestamp + 1500;

    performWait(storeRunnable(session, record0));
    performWait(storeRunnable(session, record1));
    performWait(guidsSinceRunnable(session, timestamp, expected));
  }
  
  protected void guidsSinceReturnNoRecords(Record record0) {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    long timestamp = System.currentTimeMillis();

    //  Store 1 record in the past.
    record0.lastModified = timestamp - 1000;
    performWait(storeRunnable(session, record0));

    String[] expected = {};
    performWait(guidsSinceRunnable(session, timestamp, expected));
  }

  /*
   * Tests for fetchSince
   */  
  protected void fetchSinceOneRecord(Record record0, Record record1) {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    long timestamp = System.currentTimeMillis();

    record0.lastModified = timestamp;       // Verify inclusive retrieval.
    record1.lastModified = timestamp + 3000;
    performWait(storeRunnable(session, record0));
    performWait(storeRunnable(session, record1));

    // Fetch just record1 
    String[] expectedOne = new String[1];
    expectedOne[0] = record1.guid;
    performWait(fetchSinceRunnable(session, timestamp + 1, expectedOne));

    // Fetch both, relying on inclusiveness.
    String[] expectedBoth = new String[2];
    expectedBoth[0] = record0.guid;
    expectedBoth[1] = record1.guid;
    performWait(fetchSinceRunnable(session, timestamp, expectedBoth));
  }
  
  protected void fetchSinceReturnNoRecords(Record record) {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    
    performWait(storeRunnable(session, record));

    long timestamp = System.currentTimeMillis() / 1000;

    performWait(fetchSinceRunnable(session, timestamp, new String[] {}));
  }
  
  protected void fetchOneRecordByGuid(Record record0, Record record1) {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    
    performWait(storeRunnable(session, record0));
    performWait(storeRunnable(session, record1));
    
    String[] expected = new String[] { record0.guid };
    performWait(fetchRunnable(session, expected));
  }
  
  protected void fetchMultipleRecordsByGuids(Record record0,
      Record record1, Record record2) {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();

    performWait(storeRunnable(session, record0));
    performWait(storeRunnable(session, record1));
    performWait(storeRunnable(session, record2));
    
    String[] expected = new String[] { record0.guid, record2.guid };
    performWait(fetchRunnable(session, expected));
  }
  
  protected void fetchNoRecordByGuid(Record record) {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    
    performWait(storeRunnable(session, record));
    performWait(fetchRunnable(session,
                              new String[] { Utils.generateGuid() }, 
                              new String[] {}));
  }
  
  /*
   * Test wipe
   */
  protected void doWipe(Record record0, Record record1) {
    prepEmptySession();
    final AndroidBrowserRepositorySession session = getSession();
    
    // Store 2 records.
    performWait(storeRunnable(session, record0));
    performWait(storeRunnable(session, record1));
    performWait(fetchAllRunnable(session, new String[] {
        record0.guid, record1.guid
    }));

    // Wipe
    Runnable run = new Runnable() {
      @Override
      public void run() {
        session.wipe(new RepositorySessionWipeDelegate() {
          public void onWipeSucceeded() {
            performNotify();
          }
          public void onWipeFailed(Exception ex) {
            fail("wipe should have succeeded");
            performNotify();
          }
        });
      }
    };
    performWait(run);
  }
  
  /*
   * Test for store conflict resolution
   * NOTE: Must set an android ID for local record for these tests to work
   */

  /*
   * Record being stored has newer timestamp than existing local record, local
   * record has not been modified since last sync.
   */
  protected void remoteNewerTimeStamp(Record local, Record remote) {
    prepSession();
    AndroidBrowserRepositorySession session = getSession();

    // Record existing and hasn't changed since before lastSync.
    // Automatically will be assigned lastModified = current time.
    performWait(storeRunnable(session, local));

    // Create second bookmark to be passed to store. Give it a later
    // last modified timestamp and set it as same GUID.
    remote.guid = local.guid;
    remote.lastModified = local.lastModified + 1000;
    performWait(storeRunnable(session, remote));

    String[] expected = new String[] { local.guid };
    ExpectFetchDelegate delegate = new ExpectFetchDelegate(expected);
    performWait(fetchAllRunnable(session, delegate));

    // Check that one record comes back, it is the remote one, and has android
    // ID same as first.
    assertEquals(1, delegate.recordCount());
    verifyExpectedRecordReturned(remote, delegate.recordAt(0));
  }

  /*
   * Local record has a newer timestamp than the record being stored. For now,
   * we just take newer (local) record)
   */
  protected void localNewerTimeStamp(Record local, Record remote) {
    prepSession();
    AndroidBrowserRepositorySession session = getSession();

    // Local record newer.
    long timestamp = 1000000000;
    local.lastModified = timestamp;
    performWait(storeRunnable(session, local));

    // Create an older version of a record with the same GUID.
    remote.guid = local.guid;
    remote.lastModified = timestamp - 100;
    performWait(storeRunnable(session, remote));

    // Do a fetch and make sure that we get back the first (local) record.
    String[] expected = new String[] { local.guid };
    ExpectFetchDelegate delegate = new ExpectFetchDelegate(expected);
    performWait(fetchRunnable(session, expected, delegate));

    // Check that one record comes back, it is the local one
    assertEquals(1, delegate.recordCount());
    verifyExpectedRecordReturned(local, delegate.recordAt(0));
  }
  
  /*
   * Insert a record that is marked as deleted, remote has newer timestamp
   */
  
  protected void deleteRemoteNewer(Record local, Record remote) {
    prepSession();
    AndroidBrowserRepositorySession session = getSession();
    
    // Record existing and hasn't changed since before lastSync.
    // Automatically will be assigned lastModified = current time.
    performWait(storeRunnable(session, local));

    // Pass the same record to store, but mark it deleted and modified
    // more recently
    local.lastModified = local.lastModified + 1000;
    local.deleted = true;
    performWait(storeRunnable(session, local));

    String[] expected = new String[] { local.guid };
    ExpectFetchDelegate delegate = new ExpectFetchDelegate(expected);
    performWait(fetchAllRunnable(session, delegate));

    // Check that one record comes back, marked deleted and with
    // and androidId
    assertEquals(1, delegate.recordCount());
    Record record = delegate.recordAt(0);
    verifyExpectedRecordReturned(local, record);
    assertEquals(true, record.deleted);
    
  }
  
  /*
   * Insert a record that is marked as deleted, local has newer timestamp
   * and was not marked deleted (so keep it)
   */
  protected void deleteLocalNewer(Record local, Record remote) {
    prepSession();
    AndroidBrowserRepositorySession session = getSession();

    // Local record newer.
    long timestamp = 1000000000;
    local.lastModified = timestamp;
    performWait(storeRunnable(session, local));

    // Create an older version of a record with the same GUID.
    remote.guid = local.guid;
    remote.lastModified = timestamp - 100;
    remote.deleted = true;
    performWait(storeRunnable(session, remote));

    // Do a fetch and make sure that we get back the first (local) record.
    String[] expected = new String[] { local.guid };
    ExpectFetchDelegate delegate = new ExpectFetchDelegate(expected);
    performWait(fetchAllRunnable(session, delegate));

    // Check that one record comes back, it is the local one, and not deleted
    assertEquals(1, delegate.recordCount());
    Record record = delegate.recordAt(0);
    verifyExpectedRecordReturned(local, record);
    assertEquals(false, record.deleted);
  }
  
  /*
   * Insert a record that is marked as deleted, record never existed locally
   */
  protected void deleteRemoteLocalNonexistent(Record remote) {
    prepSession();
    AndroidBrowserRepositorySession session = getSession();
    
    long timestamp = 1000000000;
    
    // Pass a record marked deleted to store, doesn't exist locally
    remote.lastModified = timestamp;
    remote.deleted = true;
    performWait(storeRunnable(session, remote));

    ExpectFetchDelegate delegate = new ExpectFetchDelegate(new String[]{});
    performWait(fetchAllRunnable(session, delegate));

    // Check that no records are returned
    assertEquals(0, delegate.recordCount());
  }
  
  /*
   * Tests that don't require specific records based on type of repository.
   * These tests don't need to be overriden in subclasses, they will just work.
   */
  public void testCreateSessionNullContext() {
    Log.i("rnewman", "In testCreateSessionNullContext.");
    AndroidBrowserRepository repo = getRepository();
    try {
      repo.createSession(new DefaultSessionCreationDelegate(), null);
      fail("Should throw.");
    } catch (Exception ex) {
      assertNotNull(ex);
    }
  }
  
  public void testStoreNullRecord() {
    prepSession();
    try {
      AndroidBrowserRepositorySession session = getSession();
      session.store(null, new DefaultStoreDelegate());
      fail("Should throw.");
    } catch (Exception ex) {
      assertNotNull(ex);
    }
  }
  
  public void testFetchNoGuids() {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    performWait(fetchRunnable(session, new String[] {}, new ExpectInvalidRequestFetchDelegate())); 
  }
  
  public void testFetchNullGuids() {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    performWait(fetchRunnable(session, null, new ExpectInvalidRequestFetchDelegate())); 
  }
  
  public void testBeginOnNewSession() {
    prepEmptySessionWithoutBegin();
    getSession().begin(new ExpectBeginDelegate());
  }
  
  public void testBeginOnRunningSession() {
    prepEmptySession();
    getSession().begin(new ExpectBeginFailDelegate());
  }
  
  public void testBeginOnFinishedSession() {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    session.finish(new ExpectFinishDelegate());
    session.begin(new ExpectBeginFailDelegate());
  }
  
  public void testFinishOnFinishedSession() {
    prepEmptySession();
    AndroidBrowserRepositorySession session = getSession();
    session.finish(new ExpectFinishDelegate());
    session.finish(new ExpectFinishFailDelegate());
  }
  
  public void testFetchOnInactiveSession() {
    prepEmptySessionWithoutBegin();
    getSession().finish(new ExpectFinishFailDelegate());
  }
  
  public void testFetchOnFinishedSession() {
    prepEmptySession();
    Runnable run = new Runnable() {
      @Override
      public void run() {
        getSession().finish(new ExpectFinishDelegate());
        getSession().fetch(new String[] { Utils.generateGuid() },
            new RepositorySessionFetchRecordsDelegate() {
              @Override
              public void onFetchSucceeded(Record[] records) {
                fail("Session inactive, should fail");
                performNotify();
              }

              @Override
              public void onFetchFailed(Exception ex, Record record) {
                verifyInactiveException(ex);
                performNotify();
              }

              @Override
              public void onFetchedRecord(Record record) {
                fail("Session inactive, should fail");
                performNotify();
              }

              @Override
              public void onFetchCompleted() {
                fail("Session inactive, should fail");
                performNotify();
              }
            });
      }
    };
    performWait(run);
  }
  
  public void testGuidsSinceOnUnstartedSession() {
    prepEmptySessionWithoutBegin();
    Runnable run = new Runnable() {
      @Override
      public void run() {
        getSession().guidsSince(System.currentTimeMillis(),
            new RepositorySessionGuidsSinceDelegate() {
              public void onGuidsSinceSucceeded(String[] guids) {
                fail("Session inactive, should fail");
                performNotify();
              }

              public void onGuidsSinceFailed(Exception ex) {
                verifyInactiveException(ex);
                performNotify();
              }
            });
      }
    };
    performWait(run);
  }  
  
  private void verifyInactiveException(Exception ex) {
    if (ex.getClass() != InactiveSessionException.class) {
      fail("Wrong exception type");
    }
  }
}
