package net.mercdev.casino.bounty_hunt.types;

import java.util.UUID;

public class Bounty {

    private final long id;
    private final UUID targetUuid;
    private final UUID victimUuid;
    private final long reward;
    private final BountyStatus status;
    private final long createdAt;
    private final Long expiresAt;

    public Bounty(
            long id,
            UUID targetUuid,
            UUID victimUuid,
            long reward,
            BountyStatus status,
            long createdAt,
            Long expiresAt
    ) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.victimUuid = victimUuid;
        this.reward = reward;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public long getId(){
        return id;
    }

    public UUID getTargetUuid(){
        return targetUuid;
    }

    public UUID getVictimUuid(){
        return victimUuid;
    }

    public long getReward(){
        return reward;
    }

    public BountyStatus getBountyStatus(){
        return status;
    }

    public long getCreationTimeMillis(){
        return createdAt;
    }

    public Long getExpiryTimeMillis(){
        return expiresAt;
    }
}