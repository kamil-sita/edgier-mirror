package pl.sita.edgymirror;

import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.strongjoshua.console.CommandExecutor;
import com.strongjoshua.console.Console;
import com.strongjoshua.console.GUIConsole;
import net.mgsx.gltf.scene3d.attributes.*;
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static pl.sita.edgymirror.Const.CHUNK_SIZE;

/** First screen of the application. Displayed after the application is created. */
public class FirstScreen extends CommandExecutor implements Screen, InputProcessor {

	private final ModelBuilder builder = new ModelBuilder();
	int attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
	private final SceneManager sceneManager = new SceneManager();
	private DirectionalLightEx directionalLightEx;
	private Texture brdfLUT;
	private Cubemap diffuseCubemap;
	private Cubemap environmentCubemap;
	private Cubemap specularCubemap;
	private PerspectiveCamera camera;
	private Map<Coord2D, Chunk> world = new HashMap<>();

	private final Map<Coord2D, List<btRigidBody>> physicsChunkWorld = new HashMap<>();
	private final Set<Coord2D> physicsChunksToReload = new HashSet<>();


	private Coord2D getBelongingChunk(Coord2D coord2D) {
		int t = coord2D.getX();
		int u = coord2D.getY();

		return new Coord2D(
			getChunkCoord(t), getChunkCoord(u)
		);
	}

	private int getChunkCoord(int t) {
		if (t > 0) {
			return t / CHUNK_SIZE;
		}
		return -(-t + CHUNK_SIZE - 1)/CHUNK_SIZE;
	}

	private GameObject getObjectSlow(Coord2D coord2D, boolean top) {
		Coord2D cc = getBelongingChunk(coord2D);

		if (world.get(cc) == null) {
			world.put(cc, new Chunk());
		}

		if (top) {
			return world.get(cc).TOP_DATA[coord2D.getYBoundChunk()][coord2D.getXBoundChunk()];
		} else {
			return world.get(cc).BOT_DATA[coord2D.getYBoundChunk()][coord2D.getXBoundChunk()];
		}
	}

	private void putObjectSlow(Coord2D coord2D, boolean top, GameObject go) {
		Coord2D cc = getBelongingChunk(coord2D);

		if (world.get(cc) == null) {
			world.put(cc, new Chunk());
		}


		if (top) {
			world.get(cc).TOP_DATA[coord2D.getYBoundChunk()][coord2D.getXBoundChunk()] = go;
		} else {
			world.get(cc).BOT_DATA[coord2D.getYBoundChunk()][coord2D.getXBoundChunk()] = go;
		}
	}

	private final Matrix4 ballTransform = new Matrix4();
	private btRigidBody ballBody;

	private int pointsToGain;
	private int bestScore;

	public float getPosX() {
		return ballTransform.getTranslation(new Vector3()).x;
	}

	public float getPosY() {
		return ballTransform.getTranslation(new Vector3()).y;
	}
	public float getPosZ() {
		return ballTransform.getTranslation(new Vector3()).z;
	}

	public void setPosX(float x) {
		Vector3 v = new Vector3();
		ballTransform.getTranslation(v);
		v.x = x;
		ballTransform.setTranslation(v);
	}

	public void setPosY(float y) {
		Vector3 v = new Vector3();
		ballTransform.getTranslation(v);
		v.y = y;
		ballTransform.setTranslation(v);
	}
	public void setPosZ(float z) {
		Vector3 v = new Vector3();
		ballTransform.getTranslation(v);
		v.z = z;
		ballTransform.setTranslation(v);
	}
	private float oldX;
	private float oldY;
	private Model playerModel;
	private Model highlightModel;
	private Model arrowModel;
	private Model coinModel;
	private final Color activeSpawnColor = new Color(
		0.85f, 0.85f, 0.15f, 1
	);
	private final Color inactiveSpawnColor = new Color(
		0.45f, 0.5f, 0, 1
	);

	private Map<Color, Model> colorModelMap = new HashMap<>();

	private Array<Disposable> toDispose = new Array<>(512);
	private DirectionalShadowLight shadowLight = new DirectionalShadowLight(
		2048, 2048,
		25, 25,
		0.01f, 100f
	);

	private final BatchingRenderableProvider batchingRenderableProvider = new BatchingRenderableProvider() {
		@Override
		public void createScene(Consumer<RenderableProvider> renderableProviderConsumer) {
			createMyScene(renderableProviderConsumer);
		}
	};

	private BitmapFont fontA;

	@Override
	public void show() {
		AssetManager manager=new AssetManager();
		FileHandleResolver resolver = new InternalFileHandleResolver();
		manager.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
		manager.setLoader(BitmapFont.class, ".ttf", new FreetypeFontLoader(resolver));

		FreetypeFontLoader.FreeTypeFontLoaderParameter parms = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
		parms.fontFileName = "Square.ttf";
		parms.fontParameters.size = 40;
		manager.load("Square.ttf", BitmapFont.class, parms);

		manager.finishLoading();
		fontA = manager.get("Square.ttf");

		bulletConfig();

		validateCoordCode();

		setupConsole();

		setupGltfLib();

		generateWorld();

		createModels();
	}

	btDefaultCollisionConfiguration collisionConfig;
	btCollisionDispatcher dispatcher;
	btDbvtBroadphase broadphase;
	btDynamicsWorld dynamicsWorld;
	btConstraintSolver constraintSolver;

	private Coord2D coinCoord;

	private void bulletConfig() {
		Bullet.init();
		collisionConfig=new btDefaultCollisionConfiguration();
		dispatcher=new btCollisionDispatcher(collisionConfig);
		broadphase=new btDbvtBroadphase();
		constraintSolver = new btSequentialImpulseConstraintSolver();
		dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, constraintSolver, collisionConfig);
		dynamicsWorld.setGravity(new Vector3(0, 0, -10));

		btCollisionShape shape = new btSphereShape(0.5f);
		float mass = 1;
		Vector3 localInertia = new Vector3();
		shape.calculateLocalInertia(mass, localInertia);

		ballBody = new btRigidBody(new btRigidBody.btRigidBodyConstructionInfo(
			mass,
			new MotionState(ballTransform),
			shape,
			localInertia
		));
		ballBody.setRestitution(1000);
		ballBody.setActivationState(Collision.DISABLE_DEACTIVATION);
		dynamicsWorld.addRigidBody(ballBody);
	}

	private void setupGltfLib() {
		shadowLight.direction.set(5, -3, 1).nor();
		shadowLight.intensity = 1.5f;
		shadowLight.updateColor();
		sceneManager.environment.add(shadowLight);

		//DirectionalLightEx oneLight = new DirectionalLightEx();
		//oneLight.direction.set(5, 3, 1).nor();
		//oneLight.setColor(Color.RED);
		//sceneManager.environment.add(oneLight);

		//DirectionalLightEx twoLight = new DirectionalLightEx();
		//twoLight.direction.set(4, 3, 1).nor();
		//twoLight.setColor(Color.BLUE);
		//sceneManager.environment.add(twoLight);

		camera = new PerspectiveCamera(20, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		camera.near = 0.1f;
		camera.far = 1000f;

		directionalLightEx = new DirectionalLightEx();
		directionalLightEx.direction.set(1, -3, 1).nor();

		brdfLUT = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));
		IBLBuilder iblBuilder = IBLBuilder.createOutdoor(directionalLightEx);

		environmentCubemap = iblBuilder.buildEnvMap(1024);
		diffuseCubemap = iblBuilder.buildIrradianceMap(256);
		specularCubemap = iblBuilder.buildRadianceMap(10);
		iblBuilder.dispose();

		sceneManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
		sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));
		sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));

		sceneManager.setCamera(camera);
		sceneManager.setSkyBox(new SceneSkybox(environmentCubemap));

		sceneManager.getRenderableProviders().add(batchingRenderableProvider);
	}

	private void setupConsole() {
		Console console = new GUIConsole();
		console.setCommandExecutor(this);
		console.setDisplayKeyID(Input.Keys.APOSTROPHE);

		Gdx.input.setInputProcessor(new InputMultiplexer(console.getInputProcessor(), this));
	}

	private void createModels() {
		Material playerMaterial = new Material();
		playerMaterial.set(PBRColorAttribute.createBaseColorFactor(
			Color.WHITE
		));
		playerModel = builder.createSphere(1, 1, 1, 32, 32, playerMaterial, attrs);


		createHighlightModel();

		Material arrowMaterial = new Material();
		arrowMaterial.set(PBRColorAttribute.createBaseColorFactor(
			Color.YELLOW
		));
		arrowMaterial.set(new PBRFlagAttribute(PBRFlagAttribute.Unlit));
		arrowModel = builder.createArrow(0, 0, -2,  0, 0, -2.5f, 1f, 0.4f, 8,  GL20.GL_TRIANGLES, arrowMaterial, attrs);

		coinModel = builder.createBox(0.5f, 0.5f, 0.5f, arrowMaterial, attrs);
	}

	private void createHighlightModel() {
		Material highlightMaterial = new Material();
		highlightMaterial.set(PBRColorAttribute.createBaseColorFactor(
			new Color(0xffff0000)
		));
		builder.begin();
		builder.part("box", GL20.GL_TRIANGLES, attrs, highlightMaterial).box(-1.5f, 0, 0, .1f, .1f, 3);
		builder.part("box", GL20.GL_TRIANGLES, attrs, highlightMaterial).box(-0.5f, 0, 0, .1f, .1f, 5);
		builder.part("box", GL20.GL_TRIANGLES, attrs, highlightMaterial).box(  .5f, 0, 0, .1f, .1f, 5);
		builder.part("box", GL20.GL_TRIANGLES, attrs, highlightMaterial).box( 1.5f, 0, 0, .1f, .1f, 3);
		builder.part("box", GL20.GL_TRIANGLES, attrs, highlightMaterial).box(0, 0, -1.5f, 3f, .1f, .1f);
		builder.part("box", GL20.GL_TRIANGLES, attrs, highlightMaterial).box(0, 0, -0.5f, 5f, .1f, .1f);
		builder.part("box", GL20.GL_TRIANGLES, attrs, highlightMaterial).box(0, 0,   .5f, 5f, .1f, .1f);
		builder.part("box", GL20.GL_TRIANGLES, attrs, highlightMaterial).box(0, 0,  1.5f, 3f, .1f, .1f);
		highlightModel = builder.end();
	}

	private float restartPointX = 0;
	private float restartPointY = 0;
	private final float restartPointZ = 5;

	private List<Coord2D> bases = new ArrayList<>();

	private void validateCoordCode() {
		if (!Objects.equals(getBelongingChunk(new Coord2D(0, 0)), new Coord2D(0, 0))) {
			throw new RuntimeException();
		}
		if (!Objects.equals(getBelongingChunk(new Coord2D(CHUNK_SIZE, CHUNK_SIZE)), new Coord2D(1, 1))) {
			throw new RuntimeException();
		}
		if (!Objects.equals(getBelongingChunk(new Coord2D(2 * CHUNK_SIZE, CHUNK_SIZE)), new Coord2D(2, 1))) {
			throw new RuntimeException();
		}
		if (!Objects.equals(getBelongingChunk(new Coord2D(-1, 0)), new Coord2D(-1, 0))) {
			throw new RuntimeException("" + getBelongingChunk(new Coord2D(-1, 0)));
		}
		if (!Objects.equals(getBelongingChunk(new Coord2D(-1, - CHUNK_SIZE - 1)), new Coord2D(-1, -2))) {
			throw new RuntimeException();
		}
	}

	private static final int size = 5;
	private static final int sizeM1 = size - 1;
	private boolean restartTimer = true;
	private boolean purgePhysicalWorld = false;
	private boolean restartPlayerPos = true;
	private float scoreDisplay = 0;

	private void generateWorld() {
		restartPlayerPos = true;
		restartTimer = true;
		purgePhysicalWorld = true;
		world.clear();
		restartPointX = 0;
		restartPointY = 0;

		bases.clear();
		previousScore = score;
		score = 0;
		if (previousScore > 0) {
			scoreDisplay = 10;
		}

		int we = 2;
		int dx = 30;
		int r = 10;


		int bounds = (we + 1) * dx + r;

		bases = new ArrayList<>();

		for (int i = -we; i <= we; i++) {
			for (int j = -we; j <= we; j++) {

				if (i != 0 || j != 0) {
					bases.add(new Coord2D(dx * i + MathUtils.random(-r, r), dx * j + MathUtils.random(-r, r)));
				}

			}
		}

		bases.add(new Coord2D(0, 0));
		bases.add(new Coord2D(0, 15));

		setNewCoinPos(new Coord2D(0, 15));


		for (int x = -bounds; x < bounds; x++) {
			for (int y = -bounds; y < bounds; y++) {

				Coord2D base = findBase(x, y);

				if (base != null) {
					int a = -base.getX();
					int b = -base.getY();
					if ((a + x > -sizeM1 && a + x < sizeM1) && (b + y > -sizeM1 && b + y < sizeM1)) {
						if (a + x == 0 && b + y == 0) {

							putObjectSlow(
								new Coord2D(x, y), false, new GameObject(
									bottomColor(x, y), GameObject.Type.SPAWN)
							);
						} else {

							putObjectSlow(
								new Coord2D(x, y), false, new GameObject(
									bottomColor(x, y), GameObject.Type.BLOCK)
							);
						}
						continue;
					}

					if (
						(Math.abs(a + x) == sizeM1 && (b + y > -size && b + y < size))
							|| (Math.abs(b + y) == sizeM1 && (a + x > -size && a + x < size))
					) {
						if ((x + y) % 2 == 0) {
							continue;
						}

						putObjectSlow(
							new Coord2D(x, y), true, new GameObject(randomTopColor(), GameObject.Type.BLOCK)
						);
						continue;
					}
				} else {
					if (MathUtils.random(0, 1.0f) < 0.2) {
						putObjectSlow(
							new Coord2D(x, y), false, new GameObject(
								bottomColor(x, y), GameObject.Type.BLOCK)
						);
					}

					//


					if (MathUtils.random(0, 1.0f) < 0.05) {
						int i = 0;
						int j = 0;
						putObjectSlow(
							new Coord2D(x + i, y + j), true, new GameObject(randomTopColor(), GameObject.Type.BLOCK)
						);

					}
				}
				//

			}
		}
	}

	private void setNewCoinPos(Coord2D coord) {
		setNewCoinPos(coord, false);
	}

	private void setNewCoinPos(Coord2D coord, boolean grantTime) {
		float ptgf = new Vector2(getPosX() - coord.getX(), getPosY() - coord.getY()).len();;
		pointsToGain = (int) ptgf;
		coinCoord = coord;
		if (grantTime) {
			timeLeft += ptgf / 5;
		}
	}

	private Coord2D findBase(int x, int y) {
		for (Coord2D baseCoord : bases) {

			if (Math.abs(baseCoord.getX() - x) <= sizeM1 && Math.abs(baseCoord.getY() - y) <= sizeM1) {
				return baseCoord;
			}

		}
		return null;
	}

	private void add(int x, int y, boolean isTop) {
		btCollisionShape shape = new btBoxShape(new Vector3(0.5f, 0.5f, 0.5f));
		Matrix4 matrix4 = new Matrix4();
		float mass = 0;
		Vector3 localInertia = new Vector3();
		shape.calculateLocalInertia(mass, localInertia);
		Vector3 vector3 = matrix4.getTranslation(new Vector3());
		vector3.set(x, y, isTop ? 1 : 0);
		matrix4.setTranslation(vector3);
		MotionState motionState = new MotionState(matrix4);
		motionState.setName("floor");
		btRigidBody body = new btRigidBody(new btRigidBody.btRigidBodyConstructionInfo(
			mass,
			motionState,
			shape,
			localInertia
		));
		body.setCollisionFlags(btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT);
		body.setActivationState(Collision.DISABLE_DEACTIVATION);
		dynamicsWorld.addRigidBody(body);
		Coord2D chunkCoord = getBelongingChunk(new Coord2D(x, y));
		physicsChunkWorld.putIfAbsent(chunkCoord, new ArrayList<>());
		List<btRigidBody> l = physicsChunkWorld.get(chunkCoord);
		l.add(body);
	}

	private Color randomTopColor() {
		int c = MathUtils.random(0, 15);
		if (c < 0) {
			c += 16;
		}

		if ((c == 0 )||( c == 3 )||(c == 5 )||( c == 11)) {
			return toColor(0.25f, 0.8f, 0.8f);
		}
		if ((c == 1 )||( c == 6 )||(c == 13 )||( c == 15)) {
			return toColor(0.5f, 0.8f, 0.8f);
		}
		if ((c == 2 )||( c == 9 )||(c == 10 )||( c == 12)) {
			return toColor(0.75f, 0.8f, 0.8f);
		}
		return toColor(0, 0.8f, 0.8f);
	}

	private Color bottomColor(int x, int y) {
		int c = (13 * x + 11 * y + MathUtils.random(0, 15)) % 16;
		if (c < 0) {
			c += 16;
		}

		if ((c == 0 )||( c == 3 )||(c == 5 )||( c == 11)) {
			return toColor(0.25f, 0.3f, 0.8f);
		}
		if ((c == 1 )||( c == 6 )||(c == 13 )||( c == 15)) {
			return toColor(0.5f, 0.3f, 0.8f);
		}
		if ((c == 2 )||( c == 9 )||(c == 10 )||( c == 12)) {
			return toColor(0.75f, 0.3f, 0.8f);
		}
		return toColor(0, 0.3f, 0.8f);
	}

	public Color randomColor(float sat, float b) {
		return toColor(MathUtils.random(0, 1.0f), sat, b);
	}

	public Color toColor(float h, float s, float b) {

		int rgb = java.awt.Color.HSBtoRGB(h, s, b);
		java.awt.Color cc = new java.awt.Color(rgb);
		return new Color(cc.getRed() / 255.0f, cc.getGreen() / 255.0f, cc.getBlue() / 255.0f, 1);
	}

	private void createMyScene(Consumer<RenderableProvider> renderableProviderConsumer) {
		ModelInstance playerModelInstance = new ModelInstance(playerModel, getPosX(), getPosZ(), getPosY());
		renderableProviderConsumer.accept(playerModelInstance);

		Coord2D center = getPlayerPosition();


		renderLayer(renderableProviderConsumer, false, 0, center);
		renderLayer(renderableProviderConsumer, true, 1, center);

		Vector3 highlightPos = getMouseOnPlaneSnapped(Gdx.input.getX(), Gdx.input.getY());

		if (highlightPos != null) {
			ModelInstance highlightInstance = new ModelInstance(highlightModel, highlightPos.x, 0.5f, highlightPos.z);
			renderableProviderConsumer.accept(highlightInstance);
		}

		ModelInstance arrowInstance = new ModelInstance(arrowModel, getPosX(), getPosZ(), getPosY());
		arrowInstance.transform.rotateTowardTarget(new Vector3(coinCoord.getX(), 1, coinCoord.getY()), Vector3.Y);
		renderableProviderConsumer.accept(arrowInstance);

		ModelInstance coinInstance = new ModelInstance(coinModel, coinCoord.getX(), 1, coinCoord.getY());
		Matrix4 mat = coinInstance.transform;
		mat.rotate(0.1f, 0.13f, 0.19f, time * 200);
		renderableProviderConsumer.accept(coinInstance);
	}

	private Coord2D getPlayerPosition() {
		int flrX = MathUtils.floor(getPosX());
		int flrY = MathUtils.floor(getPosY());
		return new Coord2D(flrX, flrY);
	}

	private void renderLayer(Consumer<RenderableProvider> renderableProviderConsumer, boolean isTop, int z, Coord2D center) {
		int chunkRenderRadius = 3;
		int radius = 16;

		Coord2D spawnCoord = new Coord2D((int) restartPointX, (int) restartPointY);

		goInRadiusProvider(
			(go, x, y) -> {
				if (go != null) {
					Model model = getModel(go, isTop, new Coord2D(x, y), spawnCoord);
					ModelInstance instance = new ModelInstance(model, x, z, y);
					renderableProviderConsumer.accept(instance);
				}
			},
			chunkRenderRadius,
			isTop,
			center,
			radius
		);
	}

	private interface RadiusConsumer {

		void consume(Coord2D coord2D);

	}

	private void goInChunkRadiusProvider(RadiusConsumer radiusConsumer, int radius, Coord2D center) {
		Coord2D chunkPos = getBelongingChunk(center);

		for (int ai = -radius; ai < radius; ai++) {

			for (int aj = -radius; aj < radius; aj++) {
				int i = ai + chunkPos.getX();
				int j = aj + chunkPos.getY();


				if (new Vector2(i - chunkPos.getX(), j - chunkPos.getY()).len() > radius) {
					continue;
				}

				Coord2D chunkPosR = new Coord2D(i, j);

				radiusConsumer.consume(chunkPosR);

			}

		}
	}

	private void goInRadiusProvider(MyConsumer consumer, int chunkRenderRadius, boolean isTop, Coord2D center, int renderRadius) {
		goInChunkRadiusProvider(
			coord2D -> {
				int i = coord2D.getX();
				int j = coord2D.getY();

				Chunk chunk = world.get(coord2D);

				if (chunk == null) {
					return;
				}

				for (int a = 0; a < CHUNK_SIZE; a++) {
					for (int b = 0; b < CHUNK_SIZE; b++) {
						int x = i * CHUNK_SIZE + a;
						int y = j * CHUNK_SIZE + b;

						if (new Vector2(center.getX() - x, center.getY() - y).len() > renderRadius) {
							continue;
						}

						GameObject go = chunk.getData(isTop, a, b);
						consumer.consume(go, x, y);
					}
				}
			},
			chunkRenderRadius,
			center
		);
	}

	private Model getModel(GameObject gameObject, boolean isTop, Coord2D coord2D, Coord2D spawnCoord) {
		boolean activeSpawn = coord2D.equals(spawnCoord);
		Color color = gameObject.getColor(activeSpawnColor, inactiveSpawnColor, activeSpawn);
		Model model = colorModelMap.get(color);
		if (model == null) {
			model = createModel(color, isTop, activeSpawn);
		}
		colorModelMap.put(color, model);
		return model;
	}

	private Model createModel(Color color, boolean isTop, boolean activeSpawn) {
		Material material = new Material();
		material.set(PBRColorAttribute.createBaseColorFactor(
			color
		));
		material.set(PBRFloatAttribute.createMetallic(
			1
		));
		if (activeSpawn) {
			material.set(new PBRFlagAttribute(PBRFlagAttribute.Unlit));
		}
		float size = isTop ? 1 : 0.9f;
		return builder.createBox(size, size, size, material, attrs);
	}

	float myCameraZoom = 7;
	float time = 0;

	private boolean firstFrameAfterReset = false;

	@Override
	public void render(float delta) {
		if (restartPlayerPos) {
			forceSetBallPosition(0, 0, restartPointZ);
			restartPlayerPos = false;
		}

		if (!restartTimer) {
			Gdx.graphics.setTitle("FPS: " + Gdx.graphics.getFramesPerSecond());
			time += delta;

			movePlayer(delta);

			dynamicsWorld.stepSimulation(delta, 5);

			shadowLight.setCenter(new Vector3(getPosX(), 0, getPosY()));

			float mltp = 12;

			if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
				mltp = 25;
			}

			myCameraZoom = MathUtils.lerp(myCameraZoom, mltp, delta);

			camera.position.set(oldX - myCameraZoom, 2 * myCameraZoom, oldY - myCameraZoom);
			camera.up.set(Vector3.Y);
			camera.lookAt(new Vector3(getPosX(), 1, getPosY()));
			camera.update();

			sceneManager.update(delta);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
			sceneManager.render();
			batchingRenderableProvider.restart();
			console.draw();

			toDispose.forEach(Disposable::dispose);
			toDispose.clear();

			updatePhysicsWorld();

			Coord2D base = findBase((int) getPosX(), (int) getPosY());
			if (base != null) {
				if (getPosZ() > 0) {
					if (Math.abs(restartPointX - base.getX())  < 0.5f &&  Math.abs(restartPointY - base.getY())  < 0.5f ) {
					} else {
						if (Math.abs(base.getX() - getPosX())  < sizeM1 - 1
							&& Math.abs(base.getY() - getPosY())  < sizeM1 - 1) {
							restartPointX = base.getX();
							restartPointY = base.getY();
							newSpawnSet = 2;
						}
					}
				}
			}

			if (new Vector2(getPosX() - coinCoord.getX(), getPosY() - coinCoord.getY()).len() < 1) {
				if (getPosZ() > 0) {
					score += pointsToGain;
					List<Coord2D> basesClone = new ArrayList<>(bases);
					basesClone.remove(base);
					int sbaseIn = MathUtils.random(basesClone.size() - 1);

					setNewCoinPos(basesClone.get(sbaseIn), true);
				}
			}

			SpriteBatch spriteBatch = new SpriteBatch();
			spriteBatch.begin();
			fontA.draw(spriteBatch, "Score: " + score + "; best score: " + bestScore, 30, Gdx.graphics.getHeight() - 30);
			fontA.draw(spriteBatch, "Time: " + (int) timeLeft, 30, Gdx.graphics.getHeight() - 100);
			if (newSpawnSet > 0) {
				fontA.draw(spriteBatch, "New spawn set", 30, Gdx.graphics.getHeight() - 160);
			}
			if (scoreDisplay > 0) {
				fontA.draw(spriteBatch, "Game over. Score: " + previousScore, 100, Gdx.graphics.getHeight() - 450);
			}
			newSpawnSet -= delta;
			scoreDisplay -= delta;
			if (score >= bestScore) {
				bestScore = score;
			}

			timeLeft -= delta;
			if (firstFrameAfterReset) {
				timeLeft = timeLeftMax;
			}
			if (timeLeft < 0) {
				fontA.draw(spriteBatch, "Your score: " + score + ". Generating new world.", 30, Gdx.graphics.getHeight() - 220);
				spriteBatch.end();
				spriteBatch.dispose();
				generateWorld();
			} else {

				spriteBatch.end();
				spriteBatch.dispose();
			}
		}
		firstFrameAfterReset = false;
		if (restartTimer) {
			timeLeft = timeLeftMax;
			restartTimer = false;
			firstFrameAfterReset = true;
		}
	}

	private static final float timeLeftMax = 420;
	private float timeLeft = timeLeftMax;

	private float newSpawnSet = 0;

	private int score = 0;
	private int previousScore = 0;

	private void updatePhysicsWorld() {
		Coord2D playerPos = getPlayerPosition();
		Set<Coord2D> physicsChunksThatShouldBeLoaded = new HashSet<>();
		goInChunkRadiusProvider(
			physicsChunksThatShouldBeLoaded::add,
			2,
			playerPos
		);

		if (purgePhysicalWorld) {
			physicsChunksThatShouldBeLoaded.clear();
			purgePhysicalWorld = false;
		}

		banish(physicsChunksThatShouldBeLoaded);
		physicsChunksThatShouldBeLoaded.addAll(physicsChunksToReload);

		for (Coord2D physicsChunkThatShouldBeLoaded : physicsChunksThatShouldBeLoaded) {

			if (physicsChunkWorld.containsKey(physicsChunkThatShouldBeLoaded)) {
				continue;
			}

			Chunk localWorld = world.get(physicsChunkThatShouldBeLoaded);

			if (localWorld == null) {
				continue;
			}

			for (int y = 0; y < CHUNK_SIZE; y++) {
				for (int x = 0; x < CHUNK_SIZE; x++) {

					int effX = CHUNK_SIZE * physicsChunkThatShouldBeLoaded.getX() + x;
					int effY = CHUNK_SIZE * physicsChunkThatShouldBeLoaded.getY() + y;

					addToPhysicsWorldIfExists(effX, effY, false);
					addToPhysicsWorldIfExists(effX, effY, true);

				}
			}

		}


		physicsChunksToReload.clear();
	}

	private void addToPhysicsWorldIfExists(int effX, int effY, boolean top) {
		GameObject obj = getObjectSlow(new Coord2D(effX, effY), top);
		if (obj != null) {
			add(effX, effY, top);
		}
	}

	private void banish(Set<Coord2D> physicsChunksThatShouldBeLoaded) {
		Set<Coord2D> toBanish = new HashSet<>(physicsChunksToReload);
		physicsChunkWorld.keySet().forEach(
			coord -> {
				if (!physicsChunksThatShouldBeLoaded.contains(coord)) {
					toBanish.add(coord);
				}
			}
		);

		for (Coord2D coord2D : toBanish) {
			List<btRigidBody> res = physicsChunkWorld.get(coord2D);
			if (res == null) {
				res = new ArrayList<>();
			}

			for (btRigidBody rigidBody : res) {
				dynamicsWorld.removeRigidBody(rigidBody);
				rigidBody.dispose();
			}

			physicsChunkWorld.remove(coord2D);
		}
	}

	private void movePlayer(float delta) {
		int dirX = 0;
		int dirY = 0;

		if (Gdx.input.isKeyPressed(Input.Keys.A)) {
			dirX -= 1;
		}
		if (Gdx.input.isKeyPressed(Input.Keys.D)) {
			dirX += 1;
		}
		if (Gdx.input.isKeyPressed(Input.Keys.W)) {
			dirY -= 1;
		}
		if (Gdx.input.isKeyPressed(Input.Keys.S)) {
			dirY += 1;
		}

		float spd = 15;

		float actX = 0;
		float actY = 0;

		if (dirX != 0 || dirY != 0) {
			float len = new Vector2(dirX, dirY).len();
			float dirXAdj = dirX / len;
			float dirYAdj = dirY / len;

			actX = -1 * (float) (Math.cos(Math.toRadians(45)) * dirXAdj + Math.sin(Math.toRadians(45)) * dirYAdj);
			actY = -1 * (float) (Math.cos(Math.toRadians(45)) * dirYAdj + Math.sin(Math.toRadians(-45)) * dirXAdj);
		}

		float actXAdj = actX * spd * delta;
		float actYAdj = actY * spd * delta;

		if (Math.abs(getPosZ()) < 2.5) {
			ballBody.applyCentralImpulse(new Vector3(actXAdj, actYAdj, 0));
		}

		oldX = MathUtils.lerp(oldX, getPosX(), 0.04f);
		oldY = MathUtils.lerp(oldY, getPosY(), 0.04f);

		if (getPosZ() < -10) {
			float x = restartPointX;
			float y = restartPointY;
			float z = restartPointZ;
			forceSetBallPosition(x, y, z);
		}



		AtomicInteger integer = new AtomicInteger();

		goInRadiusProvider(
			(go1, x, y) -> {
				if (go1 != null) {
					integer.incrementAndGet();
				}
			},
			1,
			false,
			new Coord2D((int) getPosX(), (int) getPosY()),
			2
		);
	}

	private void forceSetBallPosition(float x, float y, float z) {
		MotionState state = (MotionState) ballBody.getMotionState();
		Matrix4 m4 = state.transform;
		Vector3 translation = m4.getTranslation(new Vector3());
		translation.x = x;
		translation.y = y;
		translation.z = z;
		m4.setTranslation(translation);
		state.setWorldTransform(m4);
		ballBody.clearForces();
		ballBody.setMotionState(new MotionState(m4));
		ballBody.setLinearVelocity(new Vector3());
		ballBody.setAngularVelocity(new Vector3());
	}

	@Override
	public void resize(int width, int height) {
		console.setSize((int) (width * 0.3), (int) (height * 0.3));
		sceneManager.updateViewport(width, height);
	}

	public boolean keyDown (int keycode) {
		return false;
	}

	public boolean keyUp (int keycode) {
		return false;
	}

	public boolean keyTyped (char character) {
		return false;
	}

	boolean interested = true;
	public boolean touchDown (int screenX, int screenY, int pointer, int button) {

		if (interested) {
			Vector3 intersection = getMouseOnPlaneSnapped(screenX, screenY);

			if (intersection != null) {
				for (int xa = -2; xa <= 2; xa++) {
					for (int ya = -2; ya <= 2; ya++) {

						if (new Vector2(xa, ya).len() > 2) {
							continue;
						}

						Coord2D pos = new Coord2D((int) (intersection.x + xa), (int) (intersection.z + ya));
						physicsChunksToReload.add(getBelongingChunk(pos));
						GameObject bot = getObjectSlow(pos, false);
						GameObject top = getObjectSlow(pos, true);

						if (bot != null) {
							if (bot.getType() == GameObject.Type.SPAWN) {
								continue;
							}
						}

						if (bot != null && top != null) {
							continue;
						}
						if (bot != null) {
							putObjectSlow(pos, false, null);
							putObjectSlow(pos, true, new GameObject(randomTopColor(), GameObject.Type.BLOCK));
						}
						if (top != null) {
							putObjectSlow(pos, false, null);
							putObjectSlow(pos, true, null);
						}
						if (bot == null && top == null) {
							putObjectSlow(pos, false, new GameObject(bottomColor(
								pos.getX(), pos.getY()
							), GameObject.Type.BLOCK));
						}

					}
				}


				return true;
			}
			interested = false;
		}
		return false;
	}

	private Vector3 getMouseOnPlane(int screenX, int screenY) {
		Ray ray = camera.getPickRay(screenX, screenY);

		Plane plane = new Plane(
			new Vector3(0, .5f, 0),
			new Vector3(1, .5f, 0),
			new Vector3(0, .5f, 1)
		);

		Vector3 intersection = new Vector3();

		boolean present = Intersector.intersectRayPlane(ray, plane, intersection);

		if (present) {
			return intersection;
		} else {
			return null;
		}
	}

	private Vector3 getMouseOnPlaneSnapped(int screenX, int screenY) {
		Vector3 highlightPos = getMouseOnPlane(screenX, screenY);

		if (highlightPos != null) {
			highlightPos.x = (float) Math.floor(highlightPos.x);
			highlightPos.z = (float) Math.floor(highlightPos.z);
		}
		return highlightPos;
	}

	public boolean touchUp (int screenX, int screenY, int pointer, int button) {
		interested = true;
		return false;
	}

	public boolean touchDragged (int screenX, int screenY, int pointer) {
		return false;
	}

	@Override
	public boolean mouseMoved (int screenX, int screenY) {
		return false;
	}

	@Override
	public boolean scrolled (float amountX, float amountY) {
		return false;
	}

	@Override
	public void pause() {
		// Invoked when your application is paused.
	}

	@Override
	public void resume() {
		// Invoked when your application is resumed after pause.
	}

	@Override
	public void hide() {
		// This method is called when another screen replaces this one.
	}

	@Override
	public void dispose() {
		// Destroy screen's assets here.
		console.dispose();
	}

	public static class MotionState extends btMotionState {

		String name = "def";
		final Matrix4 transform;

		public void setName(String name) {
			this.name = name;
		}

		public MotionState(Matrix4 transform) {
			this.transform = transform;
		}

		@Override
		public void getWorldTransform(Matrix4 worldTrans) {
			worldTrans.set(transform);
		}

		@Override
		public void setWorldTransform(Matrix4 worldTrans) {
			transform.set(worldTrans);
		}
	}
}