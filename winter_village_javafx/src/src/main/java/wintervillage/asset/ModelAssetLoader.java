package wintervillage.asset;

import de.javagl.jgltf.model.AccessorByteData;
import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AccessorShortData;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.ImageModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.v2.MaterialModelV2;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Affine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads glTF 2.0 / GLB assets and converts them into JavaFX 3D nodes.
 *
 * <p>JavaFX has no built-in glTF importer, so this class uses the jgltf model
 * parser and creates {@link TriangleMesh}/{@link MeshView} nodes. glTF is
 * Y-up and the Monopoly world is Y-up, so no axis conversion is applied. The
 * GLB origin is preserved; callers apply scale/rotation/translation.</p>
 *
 * <p>Supported: static triangle geometry, node hierarchy, multiple materials,
 * base-color textures (embedded or referenced), emissive factor/texture.
 * JavaFX {@link PhongMaterial} cannot reproduce metallic/roughness PBR,
 * normal maps or clearcoat; those are approximated or ignored explicitly.</p>
 */
public final class ModelAssetLoader {

    private static final Logger LOG = Logger.getLogger(ModelAssetLoader.class.getName());

    /** Batched prototypes are cached because the city reuses the same GLBs. */
    private static final Map<String, Group> BATCHED_CACHE = new HashMap<>();

    private ModelAssetLoader() {
    }

    /** Load a GLB/glTF asset. Must be called on the JavaFX Application Thread. */
    public static Group load(Path assetPath) throws IOException {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("ModelAssetLoader.load must run on the JavaFX Application Thread");
        }
        if (assetPath == null) {
            throw new IOException("Asset path is null");
        }
        Path path = assetPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IOException("3D asset not found: " + path);
        }

        GltfModel model;
        try {
            model = new GltfModelReader().read(path);
        } catch (IOException e) {
            throw new IOException("Could not parse glTF asset: " + path + " (" + e.getMessage() + ")", e);
        }

        Path baseDir = path.getParent();
        Group root = new Group();
        root.setId(stripExtension(path.getFileName().toString()));

        Map<MaterialModel, PhongMaterial> materials = new IdentityHashMap<>();
        MaterialStats stats = new MaterialStats();
        int meshViewCount = 0;

        List<SceneModel> scenes = model.getSceneModels();
        if (scenes.isEmpty()) {
            for (NodeModel node : model.getNodeModels()) {
                if (node.getParent() == null) {
                    root.getChildren().add(convertNode(node, baseDir, materials, stats, new IdentityHashMap<>()));
                }
            }
        } else {
            for (SceneModel scene : scenes) {
                for (NodeModel node : scene.getNodeModels()) {
                    root.getChildren().add(convertNode(node, baseDir, materials, stats, new IdentityHashMap<>()));
                }
            }
        }

        // Count MeshViews for the integration report.
        for (javafx.scene.Node child : root.getChildren()) {
            meshViewCount += countMeshViews(child);
        }

        Bounds bounds = root.getBoundsInLocal();
        LOG.info(String.format(Locale.ROOT,
                "Loaded %s: %d scene root(s), %d mesh node(s), %d MeshView(s), %d material(s), %d base-color texture(s); bounds Y %.3f..%.3f",
                path.getFileName(), root.getChildren().size(), model.getMeshModels().size(), meshViewCount,
                stats.materials, stats.baseColorTextures, bounds.getMinY(), bounds.getMaxY()));

        if (stats.metallicRoughnessTextures > 0) {
            LOG.info("Ignoring metallic-roughness textures (JavaFX PhongMaterial has no PBR texture slots): "
                    + stats.metallicRoughnessTextures);
        }
        if (stats.normalMaps > 0) {
            LOG.info("Ignoring normal maps (JavaFX PhongMaterial has no normal-map slot): " + stats.normalMaps);
        }
        return root;
    }

    /**
     * Load a static asset with all primitives merged by material. This keeps
     * the draw-call count low when the same detailed building is reused many
     * times in the city. The prototype is cached and each call returns new
     * MeshViews that share the prototype's Mesh and PhongMaterial instances.
     */
    public static Group instantiateBatched(Path assetPath) throws IOException {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("ModelAssetLoader must run on the JavaFX Application Thread");
        }
        Path path = assetPath.toAbsolutePath().normalize();
        String key = path.toString();
        Group prototype = BATCHED_CACHE.get(key);
        if (prototype == null) {
            prototype = loadBatchedInternal(path);
            BATCHED_CACHE.put(key, prototype);
        }
        return copyBatched(prototype);
    }

    private static Group loadBatchedInternal(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("3D asset not found: " + path);
        }
        GltfModel model;
        try {
            model = new GltfModelReader().read(path);
        } catch (IOException e) {
            throw new IOException("Could not parse glTF asset: " + path + " (" + e.getMessage() + ")", e);
        }

        Path baseDir = path.getParent();
        Map<MaterialModel, BatchBuilder> builders = new LinkedHashMap<>();
        Map<MaterialModel, PhongMaterial> materials = new IdentityHashMap<>();
        MaterialStats stats = new MaterialStats();

        List<SceneModel> scenes = model.getSceneModels();
        if (scenes.isEmpty()) {
            for (NodeModel node : model.getNodeModels()) {
                if (node.getParent() == null) {
                    collectBatchedNode(node, builders, materials, baseDir, stats, new IdentityHashMap<>());
                }
            }
        } else {
            for (SceneModel scene : scenes) {
                for (NodeModel node : scene.getNodeModels()) {
                    collectBatchedNode(node, builders, materials, baseDir, stats, new IdentityHashMap<>());
                }
            }
        }

        Group root = new Group();
        root.setId(stripExtension(path.getFileName().toString()) + "-batched");
        for (BatchBuilder builder : builders.values()) {
            if (builder.vertexCount == 0 || builder.faces.isEmpty()) {
                continue;
            }
            TriangleMesh mesh = new TriangleMesh();
            mesh.getPoints().addAll(toFloatArray(builder.points));
            mesh.getNormals().addAll(toFloatArray(builder.normals));
            mesh.getTexCoords().addAll(toFloatArray(builder.texCoords));
            int[] faces = new int[builder.faces.size()];
            for (int i = 0; i < faces.length; i++) {
                faces[i] = builder.faces.get(i);
            }
            mesh.getFaces().addAll(faces);

            MeshView view = new MeshView(mesh);
            view.setId((builder.material == null ? "default" : builder.material.getName()) + "-batched");
            view.setMaterial(materialFor(builder.material, baseDir, materials, stats));
            boolean doubleSided = true;
            if (builder.material instanceof MaterialModelV2 material) {
                doubleSided = material.isDoubleSided();
            }
            view.setCullFace(doubleSided ? CullFace.NONE : CullFace.BACK);
            view.setMouseTransparent(true);
            root.getChildren().add(view);
        }

        Bounds bounds = root.getBoundsInLocal();
        LOG.info(String.format(Locale.ROOT,
                "Loaded batched %s: %d material MeshView(s), %d material(s), bounds Y %.3f..%.3f",
                path.getFileName(), root.getChildren().size(), stats.materials,
                bounds.getMinY(), bounds.getMaxY()));
        return root;
    }

    private static void collectBatchedNode(NodeModel node,
                                           Map<MaterialModel, BatchBuilder> builders,
                                           Map<MaterialModel, PhongMaterial> materials,
                                           Path baseDir,
                                           MaterialStats stats,
                                           Map<NodeModel, Boolean> visiting) throws IOException {
        if (visiting.put(node, Boolean.TRUE) != null) {
            throw new IOException("glTF node hierarchy contains a cycle at " + node.getName());
        }
        float[] matrix = node.computeGlobalTransform(new float[16]);

        for (MeshModel mesh : node.getMeshModels()) {
            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                collectBatchedPrimitive(primitive, matrix, builders, materials, baseDir, stats);
            }
        }
        for (NodeModel child : node.getChildren()) {
            collectBatchedNode(child, builders, materials, baseDir, stats, visiting);
        }
        visiting.remove(node);
    }

    private static void collectBatchedPrimitive(MeshPrimitiveModel primitive,
                                                float[] matrix,
                                                Map<MaterialModel, BatchBuilder> builders,
                                                Map<MaterialModel, PhongMaterial> materials,
                                                Path baseDir,
                                                MaterialStats stats) throws IOException {
        int mode = primitive.getMode();
        if (mode != 4 && mode != 5 && mode != 6) {
            return;
        }
        AccessorModel positionAccessor = primitive.getAttributes().get("POSITION");
        if (positionAccessor == null) {
            return;
        }
        float[] localPoints = readFloatAccessor(positionAccessor, 3);
        if (localPoints.length == 0) {
            return;
        }
        int vertexCount = localPoints.length / 3;
        float[] localNormals = null;
        AccessorModel normalAccessor = primitive.getAttributes().get("NORMAL");
        if (normalAccessor != null) {
            localNormals = readFloatAccessor(normalAccessor, 3);
            if (localNormals.length / 3 != vertexCount) {
                localNormals = null;
            }
        }
        float[] localTexCoords = null;
        AccessorModel texCoordAccessor = primitive.getAttributes().get("TEXCOORD_0");
        if (texCoordAccessor != null) {
            localTexCoords = readFloatAccessor(texCoordAccessor, 2);
            if (localTexCoords.length / 2 != vertexCount) {
                localTexCoords = null;
            }
        }
        int[] sourceIndices;
        if (primitive.getIndices() != null) {
            sourceIndices = readIntAccessor(primitive.getIndices());
        } else {
            sourceIndices = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                sourceIndices[i] = i;
            }
        }
        int[] triangles = triangleIndices(sourceIndices, mode);
        if (triangles.length < 3) {
            return;
        }

        float[] points = new float[localPoints.length];
        for (int i = 0; i < vertexCount; i++) {
            float x = localPoints[i * 3];
            float y = localPoints[i * 3 + 1];
            float z = localPoints[i * 3 + 2];
            points[i * 3] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
            points[i * 3 + 1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
            points[i * 3 + 2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
        }
        float[] normals;
        if (localNormals != null) {
            normals = new float[localNormals.length];
            for (int i = 0; i < vertexCount; i++) {
                float x = localNormals[i * 3];
                float y = localNormals[i * 3 + 1];
                float z = localNormals[i * 3 + 2];
                float nx = matrix[0] * x + matrix[4] * y + matrix[8] * z;
                float ny = matrix[1] * x + matrix[5] * y + matrix[9] * z;
                float nz = matrix[2] * x + matrix[6] * y + matrix[10] * z;
                double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length < 1e-9) {
                    ny = 1;
                    length = 1;
                }
                normals[i * 3] = (float) (nx / length);
                normals[i * 3 + 1] = (float) (ny / length);
                normals[i * 3 + 2] = (float) (nz / length);
            }
        } else {
            normals = computeNormals(points, triangles, 4);
        }

        MaterialModel material = primitive.getMaterialModel();
        BatchBuilder builder = builders.computeIfAbsent(material, BatchBuilder::new);
        int base = builder.vertexCount;
        for (int i = 0; i < vertexCount; i++) {
            builder.points.add(points[i * 3]);
            builder.points.add(points[i * 3 + 1]);
            builder.points.add(points[i * 3 + 2]);
            builder.normals.add(normals[i * 3]);
            builder.normals.add(normals[i * 3 + 1]);
            builder.normals.add(normals[i * 3 + 2]);
            if (localTexCoords != null) {
                builder.texCoords.add(localTexCoords[i * 2]);
                builder.texCoords.add(localTexCoords[i * 2 + 1]);
            } else {
                builder.texCoords.add(0f);
                builder.texCoords.add(0f);
            }
        }
        for (int i = 0; i < triangles.length; i += 3) {
            int a = base + triangles[i];
            int b = base + triangles[i + 1];
            int c = base + triangles[i + 2];
            builder.faces.add(a);
            builder.faces.add(a);
            builder.faces.add(b);
            builder.faces.add(b);
            builder.faces.add(c);
            builder.faces.add(c);
        }
        builder.vertexCount += vertexCount;
    }

    private static int[] triangleIndices(int[] indices, int mode) {
        if (mode == 4) {
            int count = indices.length / 3 * 3;
            int[] result = new int[count];
            System.arraycopy(indices, 0, result, 0, count);
            return result;
        }
        int triangles = Math.max(0, indices.length - 2);
        int[] result = new int[triangles * 3];
        int out = 0;
        if (mode == 5) {
            for (int i = 0; i + 2 < indices.length; i++) {
                int a = indices[i];
                int b = indices[i + 1];
                int c = indices[i + 2];
                if ((i & 1) == 1) {
                    int tmp = b; b = c; c = tmp;
                }
                result[out++] = a; result[out++] = b; result[out++] = c;
            }
        } else {
            for (int i = 1; i + 1 < indices.length; i++) {
                result[out++] = indices[0];
                result[out++] = indices[i];
                result[out++] = indices[i + 1];
            }
        }
        return result;
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static Group copyBatched(Group prototype) {
        Group copy = new Group();
        for (javafx.scene.Node node : prototype.getChildren()) {
            if (node instanceof MeshView source) {
                MeshView view = new MeshView(source.getMesh());
                view.setId(source.getId());
                view.setMaterial(source.getMaterial());
                view.setCullFace(source.getCullFace());
                view.setMouseTransparent(true);
                copy.getChildren().add(view);
            }
        }
        return copy;
    }

    private static final class BatchBuilder {
        final MaterialModel material;
        final List<Float> points = new ArrayList<>();
        final List<Float> normals = new ArrayList<>();
        final List<Float> texCoords = new ArrayList<>();
        final List<Integer> faces = new ArrayList<>();
        int vertexCount;

        BatchBuilder(MaterialModel material) {
            this.material = material;
        }
    }

    private static Group convertNode(NodeModel node,
                                     Path baseDir,
                                     Map<MaterialModel, PhongMaterial> materials,
                                     MaterialStats stats,
                                     Map<NodeModel, Boolean> visiting) throws IOException {
        if (visiting.put(node, Boolean.TRUE) != null) {
            throw new IOException("glTF node hierarchy contains a cycle at " + node.getName());
        }
        Group group = new Group();
        if (node.getName() != null) {
            group.setId(node.getName());
        }

        float[] matrix = node.getMatrix();
        if (matrix == null) {
            matrix = node.computeLocalTransform(new float[16]);
        }
        if (matrix != null && matrix.length >= 16) {
            group.getTransforms().add(new Affine(
                    matrix[0], matrix[4], matrix[8], matrix[12],
                    matrix[1], matrix[5], matrix[9], matrix[13],
                    matrix[2], matrix[6], matrix[10], matrix[14]));
        }

        for (MeshModel mesh : node.getMeshModels()) {
            int primitiveIndex = 0;
            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                MeshView view = createMeshView(mesh, primitive, primitiveIndex++, baseDir, materials, stats);
                if (view != null) {
                    group.getChildren().add(view);
                }
            }
        }

        for (NodeModel child : node.getChildren()) {
            group.getChildren().add(convertNode(child, baseDir, materials, stats, visiting));
        }
        visiting.remove(node);
        return group;
    }

    private static MeshView createMeshView(MeshModel mesh,
                                           MeshPrimitiveModel primitive,
                                           int primitiveIndex,
                                           Path baseDir,
                                           Map<MaterialModel, PhongMaterial> materials,
                                           MaterialStats stats) throws IOException {
        int mode = primitive.getMode();
        if (mode != 4 && mode != 5 && mode != 6) {
            LOG.warning("Skipping unsupported glTF primitive mode " + mode + " in " + mesh.getName());
            return null;
        }

        AccessorModel positionAccessor = primitive.getAttributes().get("POSITION");
        if (positionAccessor == null) {
            LOG.warning("Skipping primitive without POSITION: " + mesh.getName());
            return null;
        }
        float[] points = readFloatAccessor(positionAccessor, 3);
        if (points.length == 0) {
            return null;
        }
        int vertexCount = points.length / 3;

        float[] normals = null;
        AccessorModel normalAccessor = primitive.getAttributes().get("NORMAL");
        if (normalAccessor != null) {
            normals = readFloatAccessor(normalAccessor, 3);
            if (normals.length / 3 != vertexCount) {
                normals = null;
            }
        }

        float[] texCoords = null;
        AccessorModel texCoordAccessor = primitive.getAttributes().get("TEXCOORD_0");
        if (texCoordAccessor != null) {
            texCoords = readFloatAccessor(texCoordAccessor, 2);
            if (texCoords.length / 2 != vertexCount) {
                texCoords = null;
            }
        }

        int[] sourceIndices = null;
        if (primitive.getIndices() != null) {
            sourceIndices = readIntAccessor(primitive.getIndices());
        }
        int[] pointIndices;
        if (sourceIndices == null) {
            pointIndices = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                pointIndices[i] = i;
            }
        } else {
            pointIndices = sourceIndices;
        }
        if (pointIndices.length < 3) {
            return null;
        }

        if (normals == null) {
            normals = computeNormals(points, pointIndices, mode);
        }

        TriangleMesh triangleMesh = new TriangleMesh();
        triangleMesh.getPoints().addAll(points);
        triangleMesh.getNormals().addAll(normals);

        boolean hasTexCoords = texCoords != null;
        if (hasTexCoords) {
            triangleMesh.getTexCoords().addAll(texCoords);
        } else {
            triangleMesh.getTexCoords().addAll(0, 0);
        }

        int[] faces = buildFaces(pointIndices, vertexCount, mode, hasTexCoords);
        if (faces.length == 0) {
            return null;
        }
        triangleMesh.getFaces().addAll(faces);

        MeshView view = new MeshView(triangleMesh);
        view.setId(mesh.getName() + "#" + primitiveIndex);
        view.setMaterial(materialFor(primitive.getMaterialModel(), baseDir, materials, stats));
        boolean doubleSided = true;
        if (primitive.getMaterialModel() instanceof MaterialModelV2 material) {
            doubleSided = material.isDoubleSided();
        }
        view.setCullFace(doubleSided ? CullFace.NONE : CullFace.BACK);
        view.setMouseTransparent(true);
        return view;
    }

    private static PhongMaterial materialFor(MaterialModel model,
                                             Path baseDir,
                                             Map<MaterialModel, PhongMaterial> materials,
                                             MaterialStats stats) {
        if (model == null) {
            PhongMaterial fallback = new PhongMaterial(Color.WHITE);
            fallback.setSpecularColor(Color.TRANSPARENT);
            fallback.setSpecularPower(0);
            return fallback;
        }
        PhongMaterial cached = materials.get(model);
        if (cached != null) {
            return cached;
        }
        stats.materials++;

        PhongMaterial material = new PhongMaterial();
        material.setSpecularColor(Color.TRANSPARENT);
        material.setSpecularPower(0);

        if (model instanceof MaterialModelV2 pbr) {
            float[] baseFactor = pbr.getBaseColorFactor();
            Color base = baseFactor == null || baseFactor.length < 3
                    ? Color.WHITE
                    : linearToSrgb(baseFactor[0], baseFactor[1], baseFactor[2],
                    baseFactor.length > 3 ? baseFactor[3] : 1f);
            material.setDiffuseColor(base);

            TextureModel baseTexture = pbr.getBaseColorTexture();
            if (baseTexture != null) {
                stats.baseColorTextures++;
                Image image = loadTexture(baseTexture, baseDir);
                if (image != null) {
                    material.setDiffuseMap(image);
                } else {
                    LOG.warning("Could not load base-color texture for material " + model.getName());
                }
            }
            if (pbr.getMetallicRoughnessTexture() != null) {
                stats.metallicRoughnessTextures++;
            }
            if (pbr.getNormalTexture() != null) {
                stats.normalMaps++;
            }

            float[] emissiveFactor = pbr.getEmissiveFactor();
            TextureModel emissiveTexture = pbr.getEmissiveTexture();
            if (emissiveTexture != null) {
                Image image = loadTexture(emissiveTexture, baseDir);
                if (image != null) {
                    material.setSelfIlluminationMap(image);
                }
            } else if (emissiveFactor != null && max(emissiveFactor) > 0.001f) {
                material.setSelfIlluminationMap(solidColorImage(
                        emissiveColor(model, emissiveFactor)));
            }

            // JavaFX has no metallic/roughness workflow; use a very restrained
            // specular approximation so the matte scene lighting stays stable.
            double metallic = clamp01(pbr.getMetallicFactor());
            double roughness = clamp01(pbr.getRoughnessFactor());
            double specular = 0.04 + metallic * 0.28;
            if (specular > 0.06) {
                material.setSpecularColor(Color.color(
                        clamp01(base.getRed() * specular),
                        clamp01(base.getGreen() * specular),
                        clamp01(base.getBlue() * specular)));
                material.setSpecularPower(4 + (1 - roughness) * 60);
            }
        } else {
            material.setDiffuseColor(Color.WHITE);
        }

        materials.put(model, material);
        return material;
    }

    /**
     * JavaFX adds the self-illumination map on top of the lit diffuse colour,
     * which makes the warm windows look brighter and more yellow than in the
     * approved Blender render. Scale the window emission down slightly and
     * leave the lanterns hot, so the dusk mood stays readable.
     */
    private static Color emissiveColor(MaterialModel model, float[] factor) {
        String name = model.getName();
        boolean lantern = name != null
                && name.toLowerCase(Locale.ROOT).contains("lantern");
        double scale = lantern ? 0.92 : 0.60;
        double r = factor[0] * scale + 0.015;
        double g = factor[1] * scale + 0.030;
        double b = factor[2] * scale + 0.055;
        return linearToSrgb((float) r, (float) g, (float) b, 1f);
    }

    private static Image loadTexture(TextureModel texture, Path baseDir) {
        if (texture == null) {
            return null;
        }
        ImageModel imageModel = texture.getImageModel();
        if (imageModel == null) {
            return null;
        }
        try {
            ByteBuffer data = imageModel.getImageData();
            if (data != null) {
                ByteBuffer copy = data.duplicate();
                byte[] bytes = new byte[copy.remaining()];
                copy.get(bytes);
                return new Image(new ByteArrayInputStream(bytes));
            }
            String uri = imageModel.getUri();
            if (uri != null && !uri.isBlank() && !uri.startsWith("data:")) {
                Path imagePath = baseDir == null ? Path.of(uri) : baseDir.resolve(uri).normalize();
                return new Image(imagePath.toUri().toString());
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to decode texture " + imageModel.getName(), e);
        }
        return null;
    }

    private static Image solidColorImage(Color color) {
        WritableImage image = new WritableImage(1, 1);
        image.getPixelWriter().setColor(0, 0, color);
        return image;
    }

    private static float[] readFloatAccessor(AccessorModel accessor, int components) throws IOException {
        AccessorData data = accessor.getAccessorData();
        int count = accessor.getCount();
        float[] result = new float[count * components];
        if (data instanceof AccessorFloatData floats) {
            for (int i = 0; i < count; i++) {
                for (int c = 0; c < components; c++) {
                    result[i * components + c] = floats.get(i, c);
                }
            }
            return result;
        }
        int compType = accessor.getComponentType();
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < components; c++) {
                double value;
                if (data instanceof AccessorShortData shorts) {
                    value = shorts.get(i, c);
                    if (accessor.isNormalized()) {
                        value /= 65535.0;
                    }
                } else if (data instanceof AccessorByteData bytes) {
                    value = bytes.get(i, c);
                    if (accessor.isNormalized()) {
                        value /= 255.0;
                    }
                } else if (data instanceof AccessorIntData ints) {
                    value = ints.get(i, c);
                } else {
                    throw new IOException("Unsupported accessor component type: " + compType);
                }
                result[i * components + c] = (float) value;
            }
        }
        return result;
    }

    private static int[] readIntAccessor(AccessorModel accessor) throws IOException {
        AccessorData data = accessor.getAccessorData();
        int count = accessor.getCount();
        int[] result = new int[count];
        if (data instanceof AccessorIntData ints) {
            for (int i = 0; i < count; i++) {
                result[i] = (int) ints.getLong(i);
            }
        } else if (data instanceof AccessorShortData shorts) {
            for (int i = 0; i < count; i++) {
                result[i] = shorts.getInt(i);
            }
        } else if (data instanceof AccessorByteData bytes) {
            for (int i = 0; i < count; i++) {
                result[i] = bytes.getInt(i);
            }
        } else {
            throw new IOException("Unsupported index accessor type: " + data.getClass().getName());
        }
        return result;
    }

    private static int[] buildFaces(int[] indices, int vertexCount, int mode, boolean hasTexCoords) {
        int triangleCount = mode == 4 ? indices.length / 3 : Math.max(0, indices.length - 2);
        int[] faces = new int[triangleCount * 6];
        int out = 0;
        if (mode == 4) {
            for (int i = 0; i + 2 < indices.length; i += 3) {
                out = addFace(faces, out, indices[i], indices[i + 1], indices[i + 2], hasTexCoords);
            }
        } else if (mode == 5) {
            for (int i = 0; i + 2 < indices.length; i++) {
                int a = indices[i];
                int b = indices[i + 1];
                int c = indices[i + 2];
                if ((i & 1) == 1) {
                    int t = b;
                    b = c;
                    c = t;
                }
                out = addFace(faces, out, a, b, c, hasTexCoords);
            }
        } else {
            for (int i = 1; i + 1 < indices.length; i++) {
                out = addFace(faces, out, indices[0], indices[i], indices[i + 1], hasTexCoords);
            }
        }
        if (out == faces.length) {
            return faces;
        }
        int[] trimmed = new int[out];
        System.arraycopy(faces, 0, trimmed, 0, out);
        return trimmed;
    }

    private static int addFace(int[] faces, int out, int a, int b, int c, boolean hasTexCoords) {
        int texA = hasTexCoords ? a : 0;
        int texB = hasTexCoords ? b : 0;
        int texC = hasTexCoords ? c : 0;
        faces[out++] = a;
        faces[out++] = texA;
        faces[out++] = b;
        faces[out++] = texB;
        faces[out++] = c;
        faces[out++] = texC;
        return out;
    }

    private static float[] computeNormals(float[] points, int[] indices, int mode) {
        float[] normals = new float[points.length];
        int triangleCount = mode == 4 ? indices.length / 3 : Math.max(0, indices.length - 2);
        for (int t = 0; t < triangleCount; t++) {
            int ia;
            int ib;
            int ic;
            if (mode == 4) {
                ia = indices[t * 3];
                ib = indices[t * 3 + 1];
                ic = indices[t * 3 + 2];
            } else if (mode == 5) {
                ia = indices[t];
                ib = indices[t + 1];
                ic = indices[t + 2];
            } else {
                ia = indices[0];
                ib = indices[t + 1];
                ic = indices[t + 2];
            }
            float ax = points[ia * 3];
            float ay = points[ia * 3 + 1];
            float az = points[ia * 3 + 2];
            float bx = points[ib * 3];
            float by = points[ib * 3 + 1];
            float bz = points[ib * 3 + 2];
            float cx = points[ic * 3];
            float cy = points[ic * 3 + 1];
            float cz = points[ic * 3 + 2];
            float ux = bx - ax, uy = by - ay, uz = bz - az;
            float vx = cx - ax, vy = cy - ay, vz = cz - az;
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            addNormal(normals, ia, nx, ny, nz);
            addNormal(normals, ib, nx, ny, nz);
            addNormal(normals, ic, nx, ny, nz);
        }
        for (int i = 0; i < normals.length; i += 3) {
            double len = Math.sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1]
                    + normals[i + 2] * normals[i + 2]);
            if (len > 1e-9) {
                normals[i] /= len;
                normals[i + 1] /= len;
                normals[i + 2] /= len;
            } else {
                normals[i + 1] = 1;
            }
        }
        return normals;
    }

    private static void addNormal(float[] normals, int vertex, float x, float y, float z) {
        normals[vertex * 3] += x;
        normals[vertex * 3 + 1] += y;
        normals[vertex * 3 + 2] += z;
    }

    private static int countMeshViews(javafx.scene.Node node) {
        int count = node instanceof MeshView ? 1 : 0;
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                count += countMeshViews(child);
            }
        }
        return count;
    }

    private static Color linearToSrgb(float r, float g, float b, float a) {
        return new Color(linearToSrgb(r), linearToSrgb(g), linearToSrgb(b), clamp01(a));
    }

    private static double linearToSrgb(double value) {
        value = clamp01(value);
        return value <= 0.0031308
                ? 12.92 * value
                : 1.055 * Math.pow(value, 1.0 / 2.4) - 0.055;
    }

    private static float max(float[] values) {
        float max = 0;
        for (float v : values) {
            max = Math.max(max, v);
        }
        return max;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static final class MaterialStats {
        int materials;
        int baseColorTextures;
        int metallicRoughnessTextures;
        int normalMaps;
    }
}
