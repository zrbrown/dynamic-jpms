package net.eightlives.dynamicjpms.djpms;

import java.lang.reflect.Type;
import java.util.Arrays;

public class ModuleInjectListener implements ModuleResolutionListener {

    @Override
    public void moduleResolved(String moduleName, ModuleLayer moduleLayer) {
        ClassLoader classLoader = moduleLayer.findLoader(moduleName);

        moduleLayer.findModule(moduleName)
                .filter(module -> module.isAnnotationPresent(Inject.class))
                .ifPresent(module -> {
                    for (Class injectClass : module.getAnnotation(Inject.class).classes()) {
                        try {
                            Class<?> loadedClass = classLoader.loadClass(injectClass.getName());
                            Arrays.stream(loadedClass.getDeclaredConstructors()).findFirst()
                                    .ifPresent(constructor -> {
                                        for (Type parameter : constructor.getGenericParameterTypes()) {
                                            // need to probably use stream with below logic to make sure all params
                                            // can be injected before doing any construction

                                            // get type out of map
                                            parameter.getTypeName();
                                            // if exists, check for @Singleton and either use singleton or construct new
                                            //    if construct new, need way to store construction (params, constructor itself)
                                            //    notify listeners
                                            //    also notify classes in wait map
                                            // else put in wait map
                                        }
                                    });
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    // maybe use this in another listener type to do a full module scan
//                    moduleLayer.configuration().findModule(moduleName).ifPresent(resolvedModule -> {
//                        try {
//                            ModuleReader reader = resolvedModule.reference().open();
//
//                            reader.list().forEach(resourceName -> {
//
//                            });
//
//                            reader.close();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    });
                });
    }
}
