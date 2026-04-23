package com.plantogether.destination.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Acquires Postgres transaction-scoped advisory locks keyed by trip id.
 *
 * <p>Must be called inside an active transaction — the lock is released
 * automatically at commit / rollback. Serializes concurrent writes
 * (castVote, retractVote, upsertConfig) for the same trip, enforcing the
 * SIMPLE "one vote per trip per device" and RANKING "unique rank per device"
 * invariants that cannot be expressed as pure DB constraints (APPROVAL shares
 * the same table with rank IS NULL by design).
 */
@Service
public class TripLockService {

    @PersistenceContext
    private EntityManager entityManager;

    public void lock(UUID tripId) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", mix(tripId.getMostSignificantBits(), tripId.getLeastSignificantBits()))
                .getSingleResult();
    }

    /**
     * SplitMix64 finalizer on the XOR of the two halves — keeps collision probability ~2^-32.
     */
    private static long mix(long msb, long lsb) {
        long z = msb ^ lsb;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b7L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
