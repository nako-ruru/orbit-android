package com.orbit;

import java.util.Objects;

class MountPoint {
    private String rootId, uri;
    public static MountPoint createMount(String rootId, String uri) {
        MountPoint mount = new MountPoint();
        mount.setRootId(rootId);
        mount.setUri(uri);
        return mount;
    }

    private MountPoint() {
    }

    public String getRootId() {
        return rootId;
    }

    public void setRootId(String rootId) {
        this.rootId = rootId;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MountPoint that = (MountPoint) o;
        return Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uri);
    }
}
