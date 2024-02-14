package remoteio.common.lib;

import remoteio.Tags;

public class ModInfo {

    public static final String ID = "RIO";
    public static final String NAME = "RemoteIO";
    public static final String VERSION = Tags.VERSION;
    public static final String DEPENDENCIES = "required-after:Forge@[10.13.2.1291,);after:Waila";

    public static final String CLIENT = "remoteio.client.ClientProxy";
    public static final String SERVER = "remoteio.common.CommonProxy";

    public static final Object RESOURCE_PREFIX = "remoteio:";
}
