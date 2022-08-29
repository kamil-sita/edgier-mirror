package pl.sita.edgymirror;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class BatchingRenderableProvider implements RenderableProvider {

    private boolean batched = false;
    private List<RenderableProvider> providers = new ArrayList<>(1024);

    @Override
    public final void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool) {
        if (!batched) {
            batched = true;
            createScene(renderableProvider -> {
                providers.add(renderableProvider);
                renderableProvider.getRenderables(renderables, pool);
            });
        } else {
            for (int i = 0, providersSize = providers.size(); i < providersSize; i++) {
                RenderableProvider renderableProvider = providers.get(i);
                renderableProvider.getRenderables(renderables, pool);
            }
        }
    }

    public abstract void createScene(Consumer<RenderableProvider> renderableProviderConsumer);

    public void restart() {
        providers.clear();
        batched = false;
    }

}
