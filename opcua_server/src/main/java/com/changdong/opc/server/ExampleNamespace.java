package com.changdong.opc.server;

/**
 * 对节点的操作类
 */

import com.changdong.opc.ServerApplication;

import com.changdong.opc.bean.OpcGroupDto;
import com.changdong.opc.bean.OpcNodeDto;
import com.google.common.collect.ImmutableSet;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceManager;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.BaseEventTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.ServerTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.*;
import org.eclipse.milo.opcua.sdk.server.nodes.factories.NodeFactory;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.structured.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.*;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.*;

public class ExampleNamespace extends ManagedNamespaceWithLifecycle {

    public static final String NAMESPACE_URI = "urn:eclipse:milo:hello-world";

    private static final Object[][] STATIC_SCALAR_NODES = new Object[][]{
        {"Boolean", Identifiers.Boolean, new Variant(false)},
        {"Byte", Identifiers.Byte, new Variant(ubyte(0x00))},
        {"SByte", Identifiers.SByte, new Variant((byte) 0x00)},
        {"Integer", Identifiers.Integer, new Variant(32)},
        {"Int16", Identifiers.Int16, new Variant((short) 16)},
        {"Int32", Identifiers.Int32, new Variant(32)},
        {"Int64", Identifiers.Int64, new Variant(64L)},
        {"UInteger", Identifiers.UInteger, new Variant(uint(32))},
        {"UInt16", Identifiers.UInt16, new Variant(ushort(16))},
        {"UInt32", Identifiers.UInt32, new Variant(uint(32))},
        {"UInt64", Identifiers.UInt64, new Variant(ulong(64L))},
        {"Float", Identifiers.Float, new Variant(3.14f)},
        {"Double", Identifiers.Double, new Variant(3.14d)},
        {"String", Identifiers.String, new Variant("string value")},
        {"DateTime", Identifiers.DateTime, new Variant(DateTime.now())},
        {"Guid", Identifiers.Guid, new Variant(UUID.randomUUID())},
        {"ByteString", Identifiers.ByteString, new Variant(new ByteString(new byte[]{0x01, 0x02, 0x03, 0x04}))},
        {"XmlElement", Identifiers.XmlElement, new Variant(new XmlElement("<a>hello</a>"))},
        {"LocalizedText", Identifiers.LocalizedText, new Variant(LocalizedText.english("localized text"))},
        {"QualifiedName", Identifiers.QualifiedName, new Variant(new QualifiedName(1234, "defg"))},
        {"NodeId", Identifiers.NodeId, new Variant(new NodeId(1234, "abcd"))},
        {"Variant", Identifiers.BaseDataType, new Variant(32)},
        {"Duration", Identifiers.Duration, new Variant(1.0)},
        {"UtcTime", Identifiers.UtcTime, new Variant(DateTime.now())},
    };

    private static final Object[][] STATIC_ARRAY_NODES = new Object[][]{
        {"BooleanArray", Identifiers.Boolean, false},
        {"ByteArray", Identifiers.Byte, ubyte(0)},
        {"SByteArray", Identifiers.SByte, (byte) 0x00},
        {"Int16Array", Identifiers.Int16, (short) 16},
        {"Int32Array", Identifiers.Int32, 32},
        {"Int64Array", Identifiers.Int64, 64L},
        {"UInt16Array", Identifiers.UInt16, ushort(16)},
        {"UInt32Array", Identifiers.UInt32, uint(32)},
        {"UInt64Array", Identifiers.UInt64, ulong(64L)},
        {"FloatArray", Identifiers.Float, 3.14f},
        {"DoubleArray", Identifiers.Double, 3.14d},
        {"StringArray", Identifiers.String, "string value"},
        {"DateTimeArray", Identifiers.DateTime, DateTime.now()},
        {"GuidArray", Identifiers.Guid, UUID.randomUUID()},
        {"ByteStringArray", Identifiers.ByteString, new ByteString(new byte[]{0x01, 0x02, 0x03, 0x04})},
        {"XmlElementArray", Identifiers.XmlElement, new XmlElement("<a>hello</a>")},
        {"LocalizedTextArray", Identifiers.LocalizedText, LocalizedText.english("localized text")},
        {"QualifiedNameArray", Identifiers.QualifiedName, new QualifiedName(1234, "defg")},
        {"NodeIdArray", Identifiers.NodeId, new NodeId(1234, "abcd")}
    };


    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile Thread eventThread;
    private volatile boolean keepPostingEvents = true;

    private final Random random = new Random();

    private final DataTypeDictionaryManager dictionaryManager;

    private final SubscriptionModel subscriptionModel;

    ExampleNamespace(OpcUaServer server) {
        super(server, NAMESPACE_URI);

        subscriptionModel = new SubscriptionModel(server, this);
        dictionaryManager = new DataTypeDictionaryManager(getNodeContext(), NAMESPACE_URI);

        getLifecycleManager().addLifecycle(dictionaryManager);
        getLifecycleManager().addLifecycle(subscriptionModel);

        getLifecycleManager().addStartupTask(new Runnable() {
            @Override
            public void run() {
                createAndAddNodes("product1");
            }
        });

        getLifecycleManager().addLifecycle(new Lifecycle() {
            @Override
            public void startup() {
                startBogusEventNotifier();
            }

            @Override
            public void shutdown() {
                try {
                    keepPostingEvents = false;
                    eventThread.interrupt();
                    eventThread.join();
                } catch (InterruptedException ignored) {
                    // ignored
                }
            }
        });
    }

    public UaNode createAndAddNodes(String rootNodeId) {
        // Create a "HelloWorld" folder and add it to the node manager
        NodeId folderNodeId = newNodeId(rootNodeId);

        UaFolderNode folderNode = new UaFolderNode(
            getNodeContext(),
            folderNodeId,
            newQualifiedName(rootNodeId),
            LocalizedText.english(rootNodeId)
        );

        getNodeManager().addNode(folderNode);

        // Make sure our new folder shows up under the server's Objects folder.
        folderNode.addReference(new Reference(
            folderNode.getNodeId(),
            Identifiers.Organizes,
            Identifiers.ObjectsFolder.expanded(),
            false
        ));

        // Add the rest of the nodes
        addVariableNodes(folderNode, rootNodeId);

        /*addSqrtMethod(folderNode);

        addGenerateEventMethod(folderNode);

        try {
            registerCustomEnumType();
            addCustomEnumTypeVariable(folderNode);
        } catch (Exception e) {
            logger.warn("Failed to register custom enum type", e);
        }

        try {
            registerCustomStructType();
            addCustomStructTypeVariable(folderNode);
        } catch (Exception e) {
            logger.warn("Failed to register custom struct type", e);
        }

        try {
            registerCustomUnionType();
            addCustomUnionTypeVariable(folderNode);
        } catch (Exception e) {
            logger.warn("Failed to register custom struct type", e);
        }

        addCustomObjectTypeAndInstance(folderNode);*/
        return folderNode;
    }

    private void startBogusEventNotifier() {
        // Set the EventNotifier bit on Server Node for Events.
        UaNode serverNode = getServer()
            .getAddressSpaceManager()
            .getManagedNode(Identifiers.Server)
            .orElse(null);

        if (serverNode instanceof ServerTypeNode) {
            ((ServerTypeNode) serverNode).setEventNotifier(ubyte(1));

            // Post a bogus Event every couple seconds
            eventThread = new Thread(() -> {
                while (keepPostingEvents) {
                    try {
                        BaseEventTypeNode eventNode = getServer().getEventFactory().createEvent(
                            newNodeId(UUID.randomUUID()),
                            Identifiers.BaseEventType
                        );

                        eventNode.setBrowseName(new QualifiedName(1, "foo"));
                        eventNode.setDisplayName(LocalizedText.english("foo"));
                        eventNode.setEventId(ByteString.of(new byte[]{0, 1, 2, 3}));
                        eventNode.setEventType(Identifiers.BaseEventType);
                        eventNode.setSourceNode(serverNode.getNodeId());
                        eventNode.setSourceName(serverNode.getDisplayName().getText());
                        eventNode.setTime(DateTime.now());
                        eventNode.setReceiveTime(DateTime.NULL_VALUE);
                        eventNode.setMessage(LocalizedText.english("event message!"));
                        eventNode.setSeverity(ushort(2));

                        //noinspection UnstableApiUsage
                        getServer().getEventBus().post(eventNode);

                        eventNode.delete();
                    } catch (Throwable e) {
                        logger.error("Error creating EventNode: {}", e.getMessage(), e);
                    }

                    try {
                        //noinspection BusyWait
                        Thread.sleep(2_000);
                    } catch (InterruptedException ignored) {
                        // ignored
                    }
                }
            }, "bogus-event-poster");

            eventThread.start();
        }
    }

    private void addVariableNodes(UaFolderNode rootNode, String rootNodeId) {
        //String nodeId = rootNodeId + "." + groupNodeId;
        addArrayNodes(rootNode);
        addScalarNodes(rootNode);
        addAdminReadableNodes(rootNode);
        addAdminWritableNodes(rootNode);
        addDynamicNodes(rootNode);
        addDataAccessNodes(rootNode);
        //添加可写设备
        UaFolderNode uaFolderNode = addWriteOnlyNodes(rootNode, rootNodeId + ".device1", "device1");
        //添加设备属性
        addWriteSubIntegerNodes(uaFolderNode, "temperature");

        UaFolderNode uaFolderNode2 = addWriteOnlyNodes(rootNode, rootNodeId + ".device2", "device2");
        addWriteSubIntegerNodes(uaFolderNode2, "humidity");

        UaFolderNode uaFolderNode3 = addWriteOnlyNodes(rootNode, rootNodeId + ".device3", "device3");
        addWriteSubIntegerNodes(uaFolderNode3, "air_temperature");

        UaFolderNode uaFolderNode4 = addWriteOnlyNodes(rootNode, rootNodeId + ".device4", "device4");
        addWriteSubIntegerNodes(uaFolderNode4, "light");
    }

    private void addArrayNodes(UaFolderNode rootNode) {
        UaFolderNode arrayTypesFolder = new UaFolderNode(
            getNodeContext(),
            newNodeId("HelloWorld/ArrayTypes"),
            newQualifiedName("ArrayTypes"),
            LocalizedText.english("ArrayTypes")
        );

        getNodeManager().addNode(arrayTypesFolder);
        rootNode.addOrganizes(arrayTypesFolder);

        for (Object[] os : STATIC_ARRAY_NODES) {
            String name = (String) os[0];
            NodeId typeId = (NodeId) os[1];
            Object value = os[2];
            Object array = Array.newInstance(value.getClass(), 5);
            for (int i = 0; i < 5; i++) {
                Array.set(array, i, value);
            }
            Variant variant = new Variant(array);

            UaVariableNode.build(getNodeContext(), builder -> {
                builder.setNodeId(newNodeId("HelloWorld/ArrayTypes/" + name));
                builder.setAccessLevel(AccessLevel.READ_WRITE);
                builder.setUserAccessLevel(AccessLevel.READ_WRITE);
                builder.setBrowseName(newQualifiedName(name));
                builder.setDisplayName(LocalizedText.english(name));
                builder.setDataType(typeId);
                builder.setTypeDefinition(Identifiers.BaseDataVariableType);
                builder.setValueRank(ValueRank.OneDimension.getValue());
                builder.setArrayDimensions(new UInteger[]{uint(0)});
                builder.setValue(new DataValue(variant));

                builder.addAttributeFilter(new AttributeLoggingFilter(AttributeId.Value::equals));

                builder.addReference(new Reference(
                    builder.getNodeId(),
                    Identifiers.Organizes,
                    arrayTypesFolder.getNodeId().expanded(),
                    Reference.Direction.INVERSE
                ));

                return builder.buildAndAdd();
            });
        }
    }

    private void addScalarNodes(UaFolderNode rootNode) {
        UaFolderNode scalarTypesFolder = new UaFolderNode(
            getNodeContext(),
            newNodeId("HelloWorld/ScalarTypes"),
            newQualifiedName("ScalarTypes"),
            LocalizedText.english("ScalarTypes")
        );

        getNodeManager().addNode(scalarTypesFolder);
        rootNode.addOrganizes(scalarTypesFolder);

        for (Object[] os : STATIC_SCALAR_NODES) {
            String name = (String) os[0];
            NodeId typeId = (NodeId) os[1];
            Variant variant = (Variant) os[2];

            UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId("HelloWorld/ScalarTypes/" + name))
                .setAccessLevel(AccessLevel.READ_WRITE)
                .setUserAccessLevel(AccessLevel.READ_WRITE)
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(typeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();

            node.setValue(new DataValue(variant));

            node.getFilterChain().addLast(new AttributeLoggingFilter(AttributeId.Value::equals));

            getNodeManager().addNode(node);
            scalarTypesFolder.addOrganizes(node);
        }
    }

    private void addWriteSubIntegerNodes(UaFolderNode writeOnlyFolder, String name) {

        UaVariableNode intNode = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId(writeOnlyFolder.getNodeId().getIdentifier().toString() + "." + name))
                .setAccessLevel(AccessLevel.READ_WRITE)
                .setUserAccessLevel(AccessLevel.READ_WRITE)
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(Identifiers.Int32)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();
        intNode.getFilterChain().addLast(new AttributeLoggingFilter(AttributeId.Value::equals));
        intNode.setValue(new DataValue(new Variant(0)));
        getNodeManager().addNode(intNode);
        writeOnlyFolder.addOrganizes(intNode);
    }

    public UaFolderNode addWriteOnlyNodes(UaFolderNode rootNode, String groupNodeId, String groupNodeName) {
        UaFolderNode writeOnlyFolder = new UaFolderNode(
            getNodeContext(),
            newNodeId(groupNodeId),
            newQualifiedName(groupNodeName),
            LocalizedText.english(groupNodeName)
        );

        getNodeManager().addNode(writeOnlyFolder);
        rootNode.addOrganizes(writeOnlyFolder);

        UaFolderNode deviceFolder = new UaFolderNode(
                getNodeContext(),
                newNodeId("groupNodeId"),
                newQualifiedName("groupNodeName"),
                LocalizedText.english("groupNodeName")
        );

        getNodeManager().addNode(deviceFolder);
        writeOnlyFolder.addOrganizes(deviceFolder);

        /*addWriteSubIntegerNodes(writeOnlyFolder, "temperature");
        addWriteSubIntegerNodes(writeOnlyFolder, "humidity");
        addWriteSubIntegerNodes(writeOnlyFolder, "switch");
        addWriteSubIntegerNodes(writeOnlyFolder, "light");*/

        String name = "name";
        UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
            .setNodeId(newNodeId(groupNodeId + "." + name))
            .setAccessLevel(AccessLevel.READ_WRITE)
            .setUserAccessLevel(AccessLevel.READ_WRITE)
            .setBrowseName(newQualifiedName(name))
            .setDisplayName(LocalizedText.english(name))
            .setDataType(Identifiers.String)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .build();
        node.getFilterChain().addLast(new AttributeLoggingFilter(AttributeId.Value::equals));
        node.setValue(new DataValue(new Variant("can't read this")));
        getNodeManager().addNode(node);
        writeOnlyFolder.addOrganizes(node);

        String intTest = "intTest";
        UaVariableNode intNode = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId(groupNodeId + "." + intTest))
                .setAccessLevel(AccessLevel.READ_WRITE)
                .setUserAccessLevel(AccessLevel.READ_WRITE)
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(intTest))
                .setDataType(Identifiers.Int32)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();
        intNode.getFilterChain().addLast(new AttributeLoggingFilter(AttributeId.Value::equals));
        intNode.setValue(new DataValue(new Variant(0)));
        getNodeManager().addNode(intNode);
        writeOnlyFolder.addOrganizes(intNode);

        String online = ".online";
        UaVariableNode onlineNode = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId(groupNodeId + online))
                .setAccessLevel(AccessLevel.READ_WRITE)
                .setUserAccessLevel(AccessLevel.READ_WRITE)
                .setBrowseName(newQualifiedName(online))
                .setDisplayName(LocalizedText.english(online))
                .setDataType(Identifiers.Int32)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();
        onlineNode.getFilterChain().addLast(new AttributeLoggingFilter(AttributeId.Value::equals));
        onlineNode.setValue(new DataValue(new Variant(0)));

        getNodeManager().addNode(onlineNode);
        writeOnlyFolder.addOrganizes(onlineNode);

        return writeOnlyFolder;
    }

    private void addAdminReadableNodes(UaFolderNode rootNode) {
        //添加标记组
        UaFolderNode adminFolder = new UaFolderNode(
            getNodeContext(),
            newNodeId("HelloWorld/OnlyAdminCanRead"),
            newQualifiedName("OnlyAdminCanRead"),
            LocalizedText.english("OnlyAdminCanRead")
        );

        getNodeManager().addNode(adminFolder);
        rootNode.addOrganizes(adminFolder);

        //在标记组下添加标记
        String name = "read";
        UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
            .setNodeId(newNodeId("HelloWorld.OnlyAdminCanRead." + name))
                //设置读写权限
            .setAccessLevel(AccessLevel.READ_WRITE)
            .setBrowseName(newQualifiedName(name))
            .setDisplayName(LocalizedText.english(name))
            .setDataType(Identifiers.Boolean)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .build();
        node.getFilterChain().addLast(new AttributeLoggingFilter(AttributeId.Value::equals));
        node.setValue(new DataValue(new Variant(true)));

        node.getFilterChain().addLast(new RestrictedAccessFilter(identity -> {
            if ("admin".equals(identity)) {
                return AccessLevel.READ_WRITE;
            } else {
                return AccessLevel.NONE;
            }
        }));

        getNodeManager().addNode(node);
        adminFolder.addOrganizes(node);
    }

    private void addAdminWritableNodes(UaFolderNode rootNode) {
        UaFolderNode adminFolder = new UaFolderNode(
            getNodeContext(),
            newNodeId("HelloWorld/OnlyAdminCanWrite"),
            newQualifiedName("OnlyAdminCanWrite"),
            LocalizedText.english("OnlyAdminCanWrite")
        );

        getNodeManager().addNode(adminFolder);
        rootNode.addOrganizes(adminFolder);

        String name = "String";
        UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
            .setNodeId(newNodeId("HelloWorld/OnlyAdminCanWrite/" + name))
            .setAccessLevel(AccessLevel.READ_WRITE)
            .setBrowseName(newQualifiedName(name))
            .setDisplayName(LocalizedText.english(name))
            .setDataType(Identifiers.BaseDataType)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .build();

        node.setValue(new DataValue(new Variant("admin was here")));
        node.getFilterChain().addLast(new AttributeLoggingFilter(AttributeId.Value::equals));
        node.getFilterChain().addLast(new RestrictedAccessFilter(identity -> {
            if ("admin".equals(identity)) {
                return AccessLevel.READ_WRITE;
            } else {
                return AccessLevel.READ_ONLY;
            }
        }));

        getNodeManager().addNode(node);
        adminFolder.addOrganizes(node);
    }

    private void addDynamicNodes(UaFolderNode rootNode) {
        UaFolderNode dynamicFolder = new UaFolderNode(
            getNodeContext(),
            newNodeId("HelloWorld/Dynamic"),
            newQualifiedName("Dynamic"),
            LocalizedText.english("Dynamic")
        );

        getNodeManager().addNode(dynamicFolder);
        rootNode.addOrganizes(dynamicFolder);

        // Dynamic Boolean
        {
            String name = "Boolean";
            NodeId typeId = Identifiers.Boolean;
            Variant variant = new Variant(false);

            UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId("HelloWorld/Dynamic/" + name))
                .setAccessLevel(AccessLevel.READ_WRITE)
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(typeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();

            node.setValue(new DataValue(variant));

            node.getFilterChain().addLast(
                new AttributeLoggingFilter(),
                AttributeFilters.getValue(
                    ctx ->
                        new DataValue(new Variant(random.nextBoolean()))
                )
            );

            getNodeManager().addNode(node);
            dynamicFolder.addOrganizes(node);
        }

        // Dynamic Int32
        {
            String name = "Int32";
            NodeId typeId = Identifiers.Int32;
            Variant variant = new Variant(0);

            UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId("HelloWorld/Dynamic/" + name))
                .setAccessLevel(AccessLevel.READ_WRITE)
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(typeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();

            node.setValue(new DataValue(variant));

            node.getFilterChain().addLast(
                new AttributeLoggingFilter(),
                AttributeFilters.getValue(
                    ctx ->
                        new DataValue(new Variant(random.nextInt()))
                )
            );

            getNodeManager().addNode(node);
            dynamicFolder.addOrganizes(node);
        }

        // Dynamic Double
        {
            String name = "Double";
            NodeId typeId = Identifiers.Double;
            Variant variant = new Variant(0.0);

            UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId("HelloWorld/Dynamic/" + name))
                .setAccessLevel(AccessLevel.READ_WRITE)
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(typeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();

            node.setValue(new DataValue(variant));

            node.getFilterChain().addLast(
                new AttributeLoggingFilter(),
                AttributeFilters.getValue(
                    ctx ->
                        new DataValue(new Variant(random.nextDouble()))
                )
            );

            getNodeManager().addNode(node);
            dynamicFolder.addOrganizes(node);
        }
    }

    private void addDataAccessNodes(UaFolderNode rootNode) {
        // DataAccess folder
        UaFolderNode dataAccessFolder = new UaFolderNode(
            getNodeContext(),
            newNodeId("HelloWorld/DataAccess"),
            newQualifiedName("DataAccess"),
            LocalizedText.english("DataAccess")
        );

        getNodeManager().addNode(dataAccessFolder);
        rootNode.addOrganizes(dataAccessFolder);

        try {
            AnalogItemTypeNode node = (AnalogItemTypeNode) getNodeFactory().createNode(
                newNodeId("HelloWorld/DataAccess/AnalogValue"),
                Identifiers.AnalogItemType,
                new NodeFactory.InstantiationCallback() {
                    @Override
                    public boolean includeOptionalNode(NodeId typeDefinitionId, QualifiedName browseName) {
                        return true;
                    }
                }
            );

            node.setBrowseName(newQualifiedName("AnalogValue"));
            node.setDisplayName(LocalizedText.english("AnalogValue"));
            node.setDataType(Identifiers.Double);
            node.setValue(new DataValue(new Variant(3.14d)));

            node.setEURange(new Range(0.0, 100.0));

            getNodeManager().addNode(node);
            dataAccessFolder.addOrganizes(node);
        } catch (UaException e) {
            logger.error("Error creating AnalogItemType instance: {}", e.getMessage(), e);
        }
    }

    /**
     * 计算平方根方法
     * InputArguments：输入
     * OutputArguments : 输出
     * SqrtMethod 计算平方根方法，调用Math.sqrt()
     */
/*
    private void addSqrtMethod(UaFolderNode folderNode) {
        UaMethodNode methodNode = UaMethodNode.builder(getNodeContext())
            .setNodeId(newNodeId("HelloWorld/sqrt(x)"))
            .setBrowseName(newQualifiedName("sqrt(x)"))
            .setDisplayName(new LocalizedText(null, "sqrt(x)"))
            .setDescription(
                LocalizedText.english("Returns the correctly rounded positive square root of a double value."))
            .build();

        SqrtMethod sqrtMethod = new SqrtMethod(methodNode);
        methodNode.setInputArguments(sqrtMethod.getInputArguments());
        methodNode.setOutputArguments(sqrtMethod.getOutputArguments());
        methodNode.setInvocationHandler(sqrtMethod);

        getNodeManager().addNode(methodNode);

        methodNode.addReference(new Reference(
            methodNode.getNodeId(),
            Identifiers.HasComponent,
            folderNode.getNodeId().expanded(),
            false
        ));
    }

    private void addGenerateEventMethod(UaFolderNode folderNode) {
        UaMethodNode methodNode = UaMethodNode.builder(getNodeContext())
            .setNodeId(newNodeId("HelloWorld/generateEvent(eventTypeId)"))
            .setBrowseName(newQualifiedName("generateEvent(eventTypeId)"))
            .setDisplayName(new LocalizedText(null, "generateEvent(eventTypeId)"))
            .setDescription(
                LocalizedText.english("Generate an Event with the TypeDefinition indicated by eventTypeId."))
            .build();

        GenerateEventMethod generateEventMethod = new GenerateEventMethod(methodNode);
        methodNode.setInputArguments(generateEventMethod.getInputArguments());
        methodNode.setOutputArguments(generateEventMethod.getOutputArguments());
        methodNode.setInvocationHandler(generateEventMethod);

        getNodeManager().addNode(methodNode);

        methodNode.addReference(new Reference(
            methodNode.getNodeId(),
            Identifiers.HasComponent,
            folderNode.getNodeId().expanded(),
            false
        ));
    }

    private void addCustomObjectTypeAndInstance(UaFolderNode rootFolder) {
        // Define a new ObjectType called "MyObjectType".
        UaObjectTypeNode objectTypeNode = UaObjectTypeNode.builder(getNodeContext())
            .setNodeId(newNodeId("ObjectTypes/MyObjectType"))
            .setBrowseName(newQualifiedName("MyObjectType"))
            .setDisplayName(LocalizedText.english("MyObjectType"))
            .setIsAbstract(false)
            .build();

        // "Foo" and "Bar" are members. These nodes are what are called "instance declarations" by the spec.
        UaVariableNode foo = UaVariableNode.builder(getNodeContext())
            .setNodeId(newNodeId("ObjectTypes/MyObjectType.Foo"))
            .setAccessLevel(AccessLevel.READ_WRITE)
            .setBrowseName(newQualifiedName("Foo"))
            .setDisplayName(LocalizedText.english("Foo"))
            .setDataType(Identifiers.Int16)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .build();

        foo.addReference(new Reference(
            foo.getNodeId(),
            Identifiers.HasModellingRule,
            Identifiers.ModellingRule_Mandatory.expanded(),
            true
        ));

        foo.setValue(new DataValue(new Variant(0)));
        objectTypeNode.addComponent(foo);

        UaVariableNode bar = UaVariableNode.builder(getNodeContext())
            .setNodeId(newNodeId("ObjectTypes/MyObjectType.Bar"))
            .setAccessLevel(AccessLevel.READ_WRITE)
            .setBrowseName(newQualifiedName("Bar"))
            .setDisplayName(LocalizedText.english("Bar"))
            .setDataType(Identifiers.String)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .build();

        bar.addReference(new Reference(
            bar.getNodeId(),
            Identifiers.HasModellingRule,
            Identifiers.ModellingRule_Mandatory.expanded(),
            true
        ));

        bar.setValue(new DataValue(new Variant("bar")));
        objectTypeNode.addComponent(bar);

        // Tell the ObjectTypeManager about our new type.
        // This let's us use NodeFactory to instantiate instances of the type.
        getServer().getObjectTypeManager().registerObjectType(
            objectTypeNode.getNodeId(),
            UaObjectNode.class,
            UaObjectNode::new
        );

        // Add the inverse SubtypeOf relationship.
        objectTypeNode.addReference(new Reference(
            objectTypeNode.getNodeId(),
            Identifiers.HasSubtype,
            Identifiers.BaseObjectType.expanded(),
            false
        ));

        // Add type definition and declarations to address space.
        getNodeManager().addNode(objectTypeNode);
        getNodeManager().addNode(foo);
        getNodeManager().addNode(bar);

        // Use NodeFactory to create instance of MyObjectType called "MyObject".
        // NodeFactory takes care of recursively instantiating MyObject member nodes
        // as well as adding all nodes to the address space.
        try {
            UaObjectNode myObject = (UaObjectNode) getNodeFactory().createNode(
                newNodeId("HelloWorld/MyObject"),
                objectTypeNode.getNodeId()
            );
            myObject.setBrowseName(newQualifiedName("MyObject"));
            myObject.setDisplayName(LocalizedText.english("MyObject"));

            // Add forward and inverse references from the root folder.
            rootFolder.addOrganizes(myObject);

            myObject.addReference(new Reference(
                myObject.getNodeId(),
                Identifiers.Organizes,
                rootFolder.getNodeId().expanded(),
                false
            ));
        } catch (UaException e) {
            logger.error("Error creating MyObjectType instance: {}", e.getMessage(), e);
        }
    }

    private void registerCustomEnumType() throws Exception {
        NodeId dataTypeId = CustomEnumType.TYPE_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        dictionaryManager.registerEnumCodec(
            new CustomEnumType.Codec().asBinaryCodec(),
            "CustomEnumType",
            dataTypeId
        );

        UaNode node = getNodeManager().get(dataTypeId);
        if (node instanceof UaDataTypeNode) {
            UaDataTypeNode dataTypeNode = (UaDataTypeNode) node;

            dataTypeNode.setEnumStrings(new LocalizedText[]{
                LocalizedText.english("Field0"),
                LocalizedText.english("Field1"),
                LocalizedText.english("Field2")
            });
        }

        EnumField[] fields = new EnumField[]{
            new EnumField(
                0L,
                LocalizedText.english("Field0"),
                LocalizedText.NULL_VALUE,
                "Field0"
            ),
            new EnumField(
                1L,
                LocalizedText.english("Field1"),
                LocalizedText.NULL_VALUE,
                "Field1"
            ),
            new EnumField(
                2L,
                LocalizedText.english("Field2"),
                LocalizedText.NULL_VALUE,
                "Field2"
            )
        };

        EnumDefinition definition = new EnumDefinition(fields);

        EnumDescription description = new EnumDescription(
            dataTypeId,
            new QualifiedName(getNamespaceIndex(), "CustomEnumType"),
            definition,
            ubyte(BuiltinDataType.Int32.getTypeId())
        );

        dictionaryManager.registerEnumDescription(description);
    }

    private void registerCustomStructType() throws Exception {
        // Get the NodeId for the DataType and encoding Nodes.

        NodeId dataTypeId = CustomStructType.TYPE_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        NodeId binaryEncodingId = CustomStructType.BINARY_ENCODING_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        // At a minimum, custom types must have their codec registered.
        // If clients don't need to dynamically discover types and will
        // register the codecs on their own then this is all that is
        // necessary.
        // The dictionary manager will add a corresponding DataType Node to
        // the AddressSpace.

        dictionaryManager.registerStructureCodec(
            new CustomStructType.Codec().asBinaryCodec(),
            "CustomStructType",
            dataTypeId,
            binaryEncodingId
        );

        // If the custom type also needs to be discoverable by clients then it
        // needs an entry in a DataTypeDictionary that can be read by those
        // clients. We describe the type using StructureDefinition or
        // EnumDefinition and register it with the dictionary manager.
        // The dictionary manager will add all the necessary nodes to the
        // AddressSpace and generate the required dictionary bsd.xml file.

        StructureField[] fields = new StructureField[]{
            new StructureField(
                "foo",
                LocalizedText.NULL_VALUE,
                Identifiers.String,
                ValueRanks.Scalar,
                null,
                getServer().getConfig().getLimits().getMaxStringLength(),
                false
            ),
            new StructureField(
                "bar",
                LocalizedText.NULL_VALUE,
                Identifiers.UInt32,
                ValueRanks.Scalar,
                null,
                uint(0),
                false
            ),
            new StructureField(
                "baz",
                LocalizedText.NULL_VALUE,
                Identifiers.Boolean,
                ValueRanks.Scalar,
                null,
                uint(0),
                false
            ),
            new StructureField(
                "customEnumType",
                LocalizedText.NULL_VALUE,
                CustomEnumType.TYPE_ID
                    .toNodeIdOrThrow(getServer().getNamespaceTable()),
                ValueRanks.Scalar,
                null,
                uint(0),
                false
            )
        };

        StructureDefinition definition = new StructureDefinition(
            binaryEncodingId,
            Identifiers.Structure,
            StructureType.Structure,
            fields
        );

        StructureDescription description = new StructureDescription(
            dataTypeId,
            new QualifiedName(getNamespaceIndex(), "CustomStructType"),
            definition
        );

        dictionaryManager.registerStructureDescription(description, binaryEncodingId);
    }

    private void registerCustomUnionType() throws Exception {
        NodeId dataTypeId = CustomUnionType.TYPE_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        NodeId binaryEncodingId = CustomUnionType.BINARY_ENCODING_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        dictionaryManager.registerUnionCodec(
            new CustomUnionType.Codec().asBinaryCodec(),
            "CustomUnionType",
            dataTypeId,
            binaryEncodingId
        );

        StructureField[] fields = new StructureField[]{
            new StructureField(
                "foo",
                LocalizedText.NULL_VALUE,
                Identifiers.UInt32,
                ValueRanks.Scalar,
                null,
                getServer().getConfig().getLimits().getMaxStringLength(),
                false
            ),
            new StructureField(
                "bar",
                LocalizedText.NULL_VALUE,
                Identifiers.String,
                ValueRanks.Scalar,
                null,
                uint(0),
                false
            )
        };

        StructureDefinition definition = new StructureDefinition(
            binaryEncodingId,
            Identifiers.Structure,
            StructureType.Union,
            fields
        );

        StructureDescription description = new StructureDescription(
            dataTypeId,
            new QualifiedName(getNamespaceIndex(), "CustomUnionType"),
            definition
        );

        dictionaryManager.registerStructureDescription(description, binaryEncodingId);
    }

    private void addCustomEnumTypeVariable(UaFolderNode rootFolder) throws Exception {
        NodeId dataTypeId = CustomEnumType.TYPE_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        UaVariableNode customEnumTypeVariable = UaVariableNode.builder(getNodeContext())
            .setNodeId(newNodeId("HelloWorld/CustomEnumTypeVariable"))
            .setAccessLevel(AccessLevel.READ_WRITE)
            .setUserAccessLevel(AccessLevel.READ_WRITE)
            .setBrowseName(newQualifiedName("CustomEnumTypeVariable"))
            .setDisplayName(LocalizedText.english("CustomEnumTypeVariable"))
            .setDataType(dataTypeId)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .build();

        customEnumTypeVariable.setValue(new DataValue(new Variant(CustomEnumType.Field1)));

        getNodeManager().addNode(customEnumTypeVariable);

        customEnumTypeVariable.addReference(new Reference(
            customEnumTypeVariable.getNodeId(),
            Identifiers.Organizes,
            rootFolder.getNodeId().expanded(),
            false
        ));
    }

    private void addCustomStructTypeVariable(UaFolderNode rootFolder) throws Exception {
        NodeId dataTypeId = CustomStructType.TYPE_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        NodeId binaryEncodingId = CustomStructType.BINARY_ENCODING_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        UaVariableNode customStructTypeVariable = UaVariableNode.builder(getNodeContext())
            .setNodeId(newNodeId("HelloWorld/CustomStructTypeVariable"))
            .setAccessLevel(AccessLevel.READ_WRITE)
            .setUserAccessLevel(AccessLevel.READ_WRITE)
            .setBrowseName(newQualifiedName("CustomStructTypeVariable"))
            .setDisplayName(LocalizedText.english("CustomStructTypeVariable"))
            .setDataType(dataTypeId)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .build();

        CustomStructType value = new CustomStructType(
            "foo",
            uint(42),
            true,
            CustomEnumType.Field0
        );

        ExtensionObject xo = ExtensionObject.encodeDefaultBinary(
            getServer().getSerializationContext(),
            value,
            binaryEncodingId
        );

        customStructTypeVariable.setValue(new DataValue(new Variant(xo)));

        getNodeManager().addNode(customStructTypeVariable);

        customStructTypeVariable.addReference(new Reference(
            customStructTypeVariable.getNodeId(),
            Identifiers.Organizes,
            rootFolder.getNodeId().expanded(),
            false
        ));
    }

    private void addCustomUnionTypeVariable(UaFolderNode rootFolder) throws Exception {
        NodeId dataTypeId = CustomUnionType.TYPE_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        NodeId binaryEncodingId = CustomUnionType.BINARY_ENCODING_ID.toNodeIdOrThrow(getServer().getNamespaceTable());

        UaVariableNode customUnionTypeVariable = UaVariableNode.builder(getNodeContext())
            .setNodeId(newNodeId("HelloWorld/CustomUnionTypeVariable"))
            .setAccessLevel(AccessLevel.READ_WRITE)
            .setUserAccessLevel(AccessLevel.READ_WRITE)
            .setBrowseName(newQualifiedName("CustomUnionTypeVariable"))
            .setDisplayName(LocalizedText.english("CustomUnionTypeVariable"))
            .setDataType(dataTypeId)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .build();

        CustomUnionType value = CustomUnionType.ofBar("hello");

        ExtensionObject xo = ExtensionObject.encodeDefaultBinary(
            getServer().getSerializationContext(),
            value,
            binaryEncodingId
        );

        customUnionTypeVariable.setValue(new DataValue(new Variant(xo)));

        getNodeManager().addNode(customUnionTypeVariable);

        customUnionTypeVariable.addReference(new Reference(
            customUnionTypeVariable.getNodeId(),
            Identifiers.Organizes,
            rootFolder.getNodeId().expanded(),
            false
        ));
    }
*/

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }


    /**
     * 在指定的组下添加节点
     * @param writeOnlyFolder 组节点
     * @param nodeIdentifier node标识符
     * @param name node名称
     * @param accessLevel 权限等级
     * 权限 ：
     * 0 NONE
     * 1 READ_ONLY
     * 2 WRITE_ONLY
     * 3 READ_WRITE
     * 4 HISTORY_READ_ONLY
     * 5 HISTORY_READ_WRITE
     *
     * @param dataType 数据类型 见上方 HISTORY_READ_WRITE数组
     * @return
     */
    public UaFolderNode addNodeInGroup(UaFolderNode writeOnlyFolder, String nodeIdentifier, String name, ImmutableSet<AccessLevel> accessLevel, NodeId dataType, Variant value) {
        if (accessLevel == null) {
            accessLevel = AccessLevel.NONE;
        }
        if (dataType == null) {
            dataType = Identifiers.String;
        }

        String groupNodeId = writeOnlyFolder.getNodeId().getIdentifier().toString();
        UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId(groupNodeId + "." + nodeIdentifier))
                .setAccessLevel(accessLevel)
                .setUserAccessLevel(accessLevel)
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(dataType)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();
        node.getFilterChain().addLast(new AttributeLoggingFilter(AttributeId.Value::equals));
        node.setValue(new DataValue(value));
        getNodeManager().addNode(node);
        writeOnlyFolder.addOrganizes(node);
        return writeOnlyFolder;
    }

    public UaFolderNode addNodeInGroup(OpcNodeDto opcNodeDto) {
        Object[] typeAndValue = getDateType(opcNodeDto.getDataType());
        NodeId type = (NodeId) typeAndValue[0];
        Variant variant = (Variant) typeAndValue[1];
        UaFolderNode folderNode = getTreeNode(opcNodeDto.getParentNameSpace(), opcNodeDto.getParentNodeId());
        return addNodeInGroup(folderNode, opcNodeDto.getIdentifier(), opcNodeDto.getName(), getAccessLevel(opcNodeDto.getAccessLevel()), type, variant);
    }

    private ImmutableSet<AccessLevel> getAccessLevel(Integer accessLevel) {
        if (accessLevel == null) {
            return AccessLevel.NONE;
        }
        ImmutableSet<AccessLevel> accessLevels = AccessLevel.NONE;
        switch (accessLevel) {
            case 0 : return AccessLevel.NONE;
            case 1 : return AccessLevel.READ_ONLY;
            case 2 : return AccessLevel.WRITE_ONLY;
            case 3 : return AccessLevel.READ_WRITE;
            case 4 : return AccessLevel.HISTORY_READ_ONLY;
            case 5 : return AccessLevel.HISTORY_READ_WRITE;
        }
        return accessLevels;
    }

    //添加组
    public UaNode createGroup( OpcGroupDto opcGroupDto) {
        //没有父节点，直接创建根节点
        if (opcGroupDto.getParentNodeId() == null) {
            return createRootGroup(opcGroupDto.getIdentifier());
        }

        UaFolderNode parentNode = getTreeNode(opcGroupDto.getParentNameSpace(), opcGroupDto.getParentNodeId());

        String groupNodeId = opcGroupDto.getParentNodeId() + "." + opcGroupDto.getIdentifier();
        UaFolderNode folderNode = new UaFolderNode(
                getNodeContext(),
                newNodeId(groupNodeId),
                newQualifiedName(opcGroupDto.getName()),
                LocalizedText.english(opcGroupDto.getName())
        );

        getNodeManager().addNode(folderNode);
        parentNode.addOrganizes(folderNode);

        return folderNode;
    }

    public void deleteNode(int parentNamespace, String parentNodeId, int namespace, String nodeId) {
        //UaFolderNode group = getTreeNode(parentNamespace, parentNodeId);
        NodeId node = new NodeId(namespace, nodeId);
        /*UaFolderNode groupNode = (UaFolderNode) getNodeManager().get(new NodeId(parentNamespace, parentNodeId));

        assert groupNode != null;
        UaNode uaNode = groupNode.getNodeManager().get(node);
        getNodeManager().removeNode(node);
        if (uaNode != null) {
            groupNode.removeOrganizes(uaNode);
        }*/
        getNodeManager().removeNode(node);
        /*UaNode uaNode = getNodeManager().get(node);
        if (uaNode != null){
            getNodeManager().
        }
        Reference reference = getNodeManager().getReferences()
        DeleteNodesItem deleteNodesItem = new DeleteNodesItem(node, true);
        List<Reference> referenceList = getNodeManager().getReferences(new NodeId(parentNamespace, parentNodeId));
        List<Reference> referenceList1 = getNodeManager().getReferences(node);
        //getNodeManager().removeReference();
        System.out.println("aaa");*/

    }


    //创建根节点
    private UaNode createRootGroup(String rootNodeId) {
        NodeId folderNodeId = newNodeId(rootNodeId);
        UaFolderNode folderNode = new UaFolderNode(
                getNodeContext(),
                folderNodeId,
                newQualifiedName(rootNodeId),
                LocalizedText.english(rootNodeId)
        );
        getNodeManager().addNode(folderNode);
        return folderNode;
    }

    //获取节点树中的组节点
    private UaFolderNode getTreeNode(int namespace, String nodeId){
        ExampleServer server = ServerApplication.server;
        NodeId node = new NodeId(namespace, nodeId);
        AddressSpaceManager addressSpaceManager = server.getServer().getAddressSpaceManager();
        Optional<UaNode> uaNodeOptional =  addressSpaceManager.getManagedNode(node);
        return (UaFolderNode)uaNodeOptional.orElse(null);
    }

   /* private UaNode getNode(int namespace, String nodeId){
        ExampleServer server = ServerApplication.server;
        NodeId node = new NodeId(namespace, nodeId);
        AddressSpaceManager addressSpaceManager = server.getServer().getAddressSpaceManager();
        Optional<UaNode> uaNodeOptional =  addressSpaceManager.getManagedNode(node);
        return uaNodeOptional.orElse(null);
    }*/

    private Object[] getDateType(String dataType) {
        for (Object[] staticScalarNode : STATIC_SCALAR_NODES) {
            if (staticScalarNode[0].toString().equals(dataType)) {
                return new Object[]{staticScalarNode[1], staticScalarNode[2]};
            }
        }
        return null;
    }


}
