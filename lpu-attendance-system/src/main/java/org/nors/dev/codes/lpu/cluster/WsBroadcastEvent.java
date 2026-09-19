package org.nors.dev.codes.lpu.cluster;

/** In-process event so Redis subscribers and local publish share one delivery path. */
public record WsBroadcastEvent(String payload) {
}
