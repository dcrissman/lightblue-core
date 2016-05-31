/*
 Copyright 2013 Red Hat, Inc. and/or its affiliates.

 This file is part of lightblue.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.redhat.lightblue.crud;

import java.io.Serializable;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import com.redhat.lightblue.util.DefaultRegistry;
import com.redhat.lightblue.util.Resolver;

import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.metadata.ValueGenerator;

import com.redhat.lightblue.hooks.HookResolver;
import com.redhat.lightblue.hooks.CRUDHook;

import com.redhat.lightblue.interceptor.InterceptorManager;

import com.redhat.lightblue.crud.valuegenerators.GeneratorsRegistry;
import com.redhat.lightblue.extensions.valuegenerator.ValueGeneratorSupport;
import com.redhat.lightblue.extensions.ExtensionSupport;

/**
 * Factory class should be configured on initialization with all the validators
 * and hooks from all the subsystems, and used as a shared singleton object by
 * all threads.
 */
public class Factory implements Serializable {

    private static final long serialVersionUID = 1L;

    private final DefaultRegistry<String, FieldConstraintChecker> fieldConstraintValidatorRegistry = new DefaultRegistry<>();
    private final DefaultRegistry<String, EntityConstraintChecker> entityConstraintValidatorRegistry = new DefaultRegistry<>();

    private final Map<String, CRUDController> crudControllers = new HashMap<>();

    private HookResolver hookResolver;
    private final InterceptorManager interceptors = new InterceptorManager();
    private final GeneratorsRegistry generators = new GeneratorsRegistry();

    private JsonNodeFactory nodeFactory;
    private int bulkParallelExecutions = 3;

    /**
     * Adds a field constraint validator
     *
     * @param name Constraint name
     * @param checker Constraint checker
     */
    public synchronized void addFieldConstraintValidator(String name, FieldConstraintChecker checker) {
        fieldConstraintValidatorRegistry.add(name, checker);
    }

    /**
     * Adds a set of field constraint validators
     *
     * @param r A field constraint checker resolver containing a set of
     * constraint checkers
     */
    public synchronized void addFieldConstraintValidators(Resolver<String, FieldConstraintChecker> r) {
        fieldConstraintValidatorRegistry.add(r);
    }

    /**
     * Adds an entity constraint validator
     *
     * @param name Constraint name
     * @param checker Constraint checker
     */
    public synchronized void addEntityConstraintValidator(String name, EntityConstraintChecker checker) {
        entityConstraintValidatorRegistry.add(name, checker);
    }

    /**
     * Adds a set of entity constraint validators
     *
     * @param r An entity constraint checker resolver containing a set of
     * constraint checkers
     */
    public synchronized void addEntityConstraintValidators(Resolver<String, EntityConstraintChecker> r) {
        entityConstraintValidatorRegistry.add(r);
    }

    public void setBulkParallelExecutions(int i) {
        bulkParallelExecutions = i;
    }

    public int getBulkParallelExecutions() {
        return bulkParallelExecutions;
    }

    /**
     * Returns a constraint validator containing field and entity constraint
     * validators for the given entity
     */
    public ConstraintValidator getConstraintValidator(EntityMetadata md) {
        return new ConstraintValidator(fieldConstraintValidatorRegistry,
                entityConstraintValidatorRegistry,
                md);
    }

    /**
     * Adds a CRUD controller for the given backend type
     *
     * @param backendType Type of the backend for which a controller is being
     * added
     * @param controller The controller
     */
    public synchronized void addCRUDController(String backendType, CRUDController controller) {
        crudControllers.put(backendType, controller);
        registerValueGenerators(backendType, controller);
    }

    public CRUDController[] getCRUDControllers() {
        Collection<CRUDController> c = crudControllers.values();
        return c.toArray(new CRUDController[c.size()]);
    }

    /**
     * Returns a CRUD controller for the given backend type
     */
    public CRUDController getCRUDController(String backendType) {
        return crudControllers.get(backendType);
    }

    /**
     * Returns a CRUD controller for the given entity
     */
    public CRUDController getCRUDController(EntityMetadata md) {
        return getCRUDController(md.getDataStore().getBackend());
    }

    /**
     * Sets the hook resolver
     */
    public void setHookResolver(HookResolver h) {
        hookResolver = h;
    }

    /**
     * Returns the hook resolver
     */
    public HookResolver getHookResolver() {
        return hookResolver;
    }

    /**
     * Returns the hook with the given name. Returns null if hook doesn't exist
     */
    public CRUDHook getHook(String hookName) {
        return hookResolver.getHook(hookName);
    }

    /**
     * Returns the interceptor manager
     */
    public InterceptorManager getInterceptors() {
        return interceptors;
    }

    /**
     * Returns an instance of JsonNodeFactory. Never returns null, if the
     * JsonNodeFactory is not initialized, this call initializes a default
     * instance.
     */
    public JsonNodeFactory getNodeFactory() {
        if (nodeFactory == null) {
            setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        }
        return nodeFactory;
    }

    /**
     * Sets the JsonNodeFactory.
     */
    public void setNodeFactory(JsonNodeFactory factory) {
        synchronized (this) {
            nodeFactory = factory;
        }
    }

    public void registerValueGenerators(String backend, CRUDController controller) {
        if (controller instanceof ExtensionSupport) {
            ValueGeneratorSupport support = (ValueGeneratorSupport) ((ExtensionSupport) controller).getExtensionInstance(ValueGeneratorSupport.class);
            if (support != null) {
                registerValueGenerator(backend, support);
            }
        }
    }

    public void registerValueGenerator(String backend, ValueGeneratorSupport support) {
        for (ValueGenerator.ValueGeneratorType t : support.getSupportedGeneratorTypes()) {
            generators.register(t, backend, support);
        }
    }

    public ValueGeneratorSupport getValueGenerator(ValueGenerator generatorMd, String backend) {
        return generators.getValueGenerator(generatorMd, backend);
    }
}
