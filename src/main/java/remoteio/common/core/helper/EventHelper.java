package remoteio.common.core.helper;

import java.lang.reflect.Method;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * @author dmillerw
 */
public class EventHelper {

    public static void register(Object instance) {
        boolean fml = false;
        boolean forge = false;
        Class<?> clazz = instance.getClass();
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                if (method.getParameterTypes().length != 1) {
                    return; // FAIL
                }

                Class<?> event = method.getParameterTypes()[0];

                if (event.getName().contains("net.minecraftforge.event")) {
                    forge = true;
                } else if (event.getName().contains("cpw.mods.fml.common.gameevent")) {
                    fml = true;
                }
            }
        }

        if (fml && forge) {
            register(instance, BusType.BOTH);
        } else {
            if (fml) {
                register(instance, BusType.FML);
            } else if (forge) {
                register(instance, BusType.FORGE);
            }
        }
    }

    public static void register(Object instance, BusType type) {
        if (type == BusType.FML || type == BusType.BOTH) {
            FMLCommonHandler.instance().bus().register(instance);
        }

        if (type == BusType.FORGE || type == BusType.BOTH) {
            MinecraftForge.EVENT_BUS.register(instance);
        }
    }

    public static enum BusType {
        FML,
        FORGE,
        BOTH
    }
}
