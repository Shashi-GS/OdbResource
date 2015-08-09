package com.ashtonit.odb.pool;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;


/**
 * Encapsulates the URI, username and password for an OrientGraph database and
 * login so that we have a single object as a unique key for the pool.
 *
 * @author Bruce Ashton
 * @date 2015-05-28
 */
final class Key {

    /**
     * We want Key instances to be interned, so that two keys with the same URI,
     * username and password are always the same object. This is important
     * because we synchronize on these keys.
     *
     * @param uri the OrientGraph database URI
     * @param username the username to log in as
     * @param password the password for the user
     * @return a Key instance encapsulating the URI, username and password
     */
    static Key get(final String uri, final String username, final String password, final Thread thread) {
        // Interning these strings makes it quicker to retrieve keys for all
        // threads later.
        final Key newKey = new Key(uri != null ? uri.intern() : null, username != null ? username.intern() : null, password != null
                ? password.intern() : null, thread);
        final Key oldKey = KEYS.putIfAbsent(newKey, newKey);
        return oldKey != null ? oldKey : newKey;
    }


    /**
     * Returns all keys not associated with particular thread.
     * 
     * @return all keys not associated with particular thread
     */
    static Set<Key> getAllDormantKeys() {
        final Set<Key> set = new HashSet<Key>();
        KEYS.forEach(new BiConsumer<Key, Key>() {

            @Override
            public void accept(final Key key, final Key v) {
                if (key.threadName == null) {
                    set.add(key);
                }
            }
        });
        return set;
    }


    /**
     * Returns all keys with the same uri, username and password as the given
     * key.
     * 
     * @param key any key
     * @return all keys with the same uri, username and password as the given
     *         key
     */
    static Set<Key> getKeysAllThreads(final Key key) {
        final Set<Key> set = new HashSet<Key>();
        if (key != null) {
            KEYS.forEach(new BiConsumer<Key, Key>() {

                @Override
                public void accept(final Key k, final Key v) {
                    if (key.uri == k.uri && key.username == k.username) {
                        set.add(k);
                    }
                }
            });
        }
        return set;
    }


    /**
     * Removes the given key from the set of all keys.
     * 
     * @param key the key to remove
     */
    static void remove(final Key key) {
        KEYS.remove(key);
    }

    private static final Map<Key, Key> KEYS = new ConcurrentHashMap<Key, Key>();

    final String uri;
    final String username;
    final String threadName;

    volatile int perUserCount = 0;

    private final int hashCode;


    private Key(final String uri, final String username, final String password, final Thread thread) {
        this.uri = uri;
        this.username = username;
        this.threadName = thread != null ? thread.getName() : null;
        hashCode = Objects.hash(uri, username, password, thread);
    }


    /**
     * Returns true if the argument is a key with the same uri, username,
     * password and thread as this key. ususally this means they are the same
     * object, but occasionally this not be true.
     * 
     * @param o an object
     * @return true if the argument is a key with the same uri, username,
     *         password and thread as this key
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(final Object o) {
        return this == o || o instanceof Key && hashCode() == o.hashCode();
    }


    /**
     * Returns a unique hashCode for this key. The hashCode for a key is unique
     * for any combination of uri, username, password and thread.
     * 
     * @return a unique hashCode for this key
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return hashCode;
    }


    /**
     * Returns a string representation of this key.
     * 
     * @return a string representation of this key
     * @see Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Key [uri=");
        builder.append(uri);
        builder.append(", username=");
        builder.append(username);
        builder.append(", threadName=");
        builder.append(threadName);
        builder.append("]");
        return builder.toString();
    }
}
