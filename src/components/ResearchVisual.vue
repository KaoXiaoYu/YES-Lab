<script setup>
import { ChevronLeft, ChevronRight, Move, Pause, Play, RotateCcw, RotateCw } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  fullName: { type: String, default: '' },
  displayName: { type: String, default: '' },
  models: { type: Array, default: () => [] },
})

const mount = ref(null)
const activeIndex = ref(0)
const ready = ref(false)
const failed = ref(false)
const switching = ref(false)
const requestedIndex = ref(0)
const paused = ref(false)
const interactionHeld = ref(false)

const enabledModels = computed(() =>
  props.models.filter((item) => item && item.enabled !== false && item.modelUrl && item.title),
)
const activeModel = computed(() => enabledModels.value[activeIndex.value] || null)
const hasMultipleModels = computed(() => enabledModels.value.length > 1)

let disposed = false
let teardown = () => {}
let selectModel = () => {}
let resetView = () => {}

function changeSlide(delta) {
  if (!enabledModels.value.length) return
  const next = (activeIndex.value + delta + enabledModels.value.length) % enabledModels.value.length
  selectModel(next)
}

function retryModel() {
  selectModel(requestedIndex.value)
}

function releaseFocusPause(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) interactionHeld.value = false
}

onMounted(async () => {
  try {
    const [T, { GLTFLoader }, { OrbitControls }, { RoomEnvironment }] = await Promise.all([
      import('three'),
      import('three/addons/loaders/GLTFLoader.js'),
      import('three/addons/controls/OrbitControls.js'),
      import('three/addons/environments/RoomEnvironment.js'),
    ])
    if (disposed) return

    const host = mount.value
    const scene = new T.Scene()
    const camera = new T.PerspectiveCamera(34, 1, 0.01, 30)
    const renderer = new T.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' })
    renderer.setPixelRatio(Math.min(devicePixelRatio, 1.75))
    renderer.setClearColor(0x000000, 0)
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = T.PCFShadowMap
    renderer.toneMapping = T.ACESFilmicToneMapping
    renderer.toneMappingExposure = 0.85
    renderer.domElement.setAttribute('role', 'img')
    renderer.domElement.tabIndex = 0
    renderer.domElement.setAttribute('aria-description', '拖动或使用方向键旋转，Home 键重置视角')
    host.appendChild(renderer.domElement)

    const controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.enableZoom = false
    controls.enablePan = false
    controls.minPolarAngle = 0.55
    controls.maxPolarAngle = Math.PI / 2 - 0.05
    controls.rotateSpeed = 0.55
    resetView = () => {
      camera.position.set(1.15, 1.2, 2.05)
      controls.target.set(0, 0.5, 0)
      controls.update()
    }
    resetView()

    const keyboard = (event) => {
      if (event.key === 'Home') {
        event.preventDefault()
        resetView()
        return
      }
      if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return
      event.preventDefault()
      const spherical = new T.Spherical().setFromVector3(camera.position.clone().sub(controls.target))
      spherical.theta += event.key === 'ArrowLeft' ? -0.12 : event.key === 'ArrowRight' ? 0.12 : 0
      spherical.phi = T.MathUtils.clamp(
        spherical.phi + (event.key === 'ArrowUp' ? -0.1 : event.key === 'ArrowDown' ? 0.1 : 0),
        controls.minPolarAngle,
        controls.maxPolarAngle,
      )
      camera.position.copy(controls.target).add(new T.Vector3().setFromSpherical(spherical))
      controls.update()
    }
    renderer.domElement.addEventListener('keydown', keyboard)

    const pmrem = new T.PMREMGenerator(renderer)
    const room = new RoomEnvironment()
    const environment = pmrem.fromScene(room, 0.04)
    scene.environment = environment.texture
    room.dispose()
    pmrem.dispose()

    scene.add(new T.HemisphereLight(0xddefff, 0x556071, 0.8))
    const key = new T.DirectionalLight(0xfff8ed, 2.2)
    key.position.set(-1, 3, 2)
    key.castShadow = true
    key.shadow.mapSize.set(1024, 1024)
    key.shadow.camera.left = -1.6
    key.shadow.camera.right = 1.6
    key.shadow.camera.top = 1.6
    key.shadow.camera.bottom = -1.6
    key.shadow.normalBias = 0.015
    key.shadow.bias = -0.0002
    scene.add(key)
    const rim = new T.DirectionalLight(0x8fcaff, 1.4)
    rim.position.set(1, 1.5, -2)
    scene.add(rim)
    const floor = new T.Mesh(new T.PlaneGeometry(200, 200), new T.ShadowMaterial({ color: 0x193656, opacity: 0.16 }))
    floor.rotation.x = -Math.PI / 2
    floor.receiveShadow = true
    scene.add(floor)

    const resize = () => {
      if (!host.clientWidth) return
      camera.aspect = host.clientWidth / host.clientHeight
      camera.updateProjectionMatrix()
      renderer.setSize(host.clientWidth, host.clientHeight, false)
    }
    const resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host)
    resize()

    const motion = matchMedia('(prefers-reduced-motion: reduce)')
    paused.value = motion.matches
    const onMotion = () => {
      if (motion.matches) paused.value = true
    }
    motion.addEventListener('change', onMotion)

    let visible = true
    const intersectionObserver = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting
    })
    intersectionObserver.observe(host)

    const loader = new GLTFLoader()
    let currentAsset = null
    let transition = null
    let preloadEntry = null
    let loadRevision = 0
    let lastFrame = performance.now()
    let animationElapsed = 0
    let carouselElapsed = 0
    let frame = 0

    const disposeRoot = (root) => {
      if (!root) return
      const geometries = new Set()
      const materials = new Set()
      const textures = new Set()
      root.traverse((object) => {
        if (object.geometry) geometries.add(object.geometry)
        const objectMaterials = Array.isArray(object.material) ? object.material : [object.material]
        objectMaterials.filter(Boolean).forEach((material) => materials.add(material))
      })
      materials.forEach((material) => {
        Object.values(material).forEach((value) => {
          if (value?.isTexture) textures.add(value)
        })
        material.dispose()
      })
      textures.forEach((texture) => texture.dispose())
      geometries.forEach((geometry) => geometry.dispose())
      scene.remove(root)
    }

    const disposeAsset = (asset) => {
      if (!asset) return
      if (asset.mixer) {
        asset.mixer.stopAllAction()
        asset.mixer.uncacheRoot(asset.animationRoot)
      }
      disposeRoot(asset.root)
    }

    const fitModel = (model) => {
      model.updateMatrixWorld(true)
      const initialBox = new T.Box3().setFromObject(model)
      const initialSize = initialBox.getSize(new T.Vector3())
      const largestDimension = Math.max(initialSize.x, initialSize.y, initialSize.z)
      if (!Number.isFinite(largestDimension) || largestDimension <= 0) throw new Error('Model has no visible geometry')
      model.scale.multiplyScalar(1.35 / largestDimension)
      model.updateMatrixWorld(true)
      const normalizedBox = new T.Box3().setFromObject(model)
      const center = normalizedBox.getCenter(new T.Vector3())
      model.position.x -= center.x
      model.position.y -= normalizedBox.min.y
      model.position.z -= center.z
      model.updateMatrixWorld(true)
    }

    const prepareAsset = async (item) => {
      const asset = await loader.loadAsync(item.modelUrl)
      const materialStates = new Map()
      asset.scene.traverse((object) => {
        if (!object.isMesh) return
        object.castShadow = true
        object.receiveShadow = true
        const objectMaterials = Array.isArray(object.material) ? object.material : [object.material]
        objectMaterials.filter(Boolean).forEach((material) => {
          material.envMapIntensity = 0.85
          if (!materialStates.has(material)) {
            materialStates.set(material, {
              opacity: material.opacity,
              transparent: material.transparent,
              depthWrite: material.depthWrite,
            })
          }
        })
      })
      fitModel(asset.scene)
      const wrapper = new T.Group()
      wrapper.add(asset.scene)
      wrapper.position.y = 0.04
      return {
        root: wrapper,
        animationRoot: asset.scene,
        animations: asset.animations,
        mixer: null,
        materialStates,
      }
    }

    const setAssetOpacity = (asset, opacity, restore = false) => {
      asset.materialStates.forEach((state, material) => {
        material.opacity = state.opacity * opacity
        material.transparent = restore ? state.transparent : true
        material.depthWrite = restore ? state.depthWrite : false
        material.needsUpdate = true
      })
    }

    const startAssetAnimation = (asset) => {
      if (!asset.animations.length || asset.mixer) return
      asset.mixer = new T.AnimationMixer(asset.animationRoot)
      asset.mixer.clipAction(asset.animations[0]).play()
    }

    const finishTransition = (shouldPreload = true) => {
      if (!transition) return
      const { from, to } = transition
      setAssetOpacity(to, 1, true)
      to.root.position.x = 0
      disposeAsset(from)
      transition = null
      switching.value = false
      if (shouldPreload) preloadNext(activeIndex.value)
    }

    const discardPreload = () => {
      if (!preloadEntry) return
      const entry = preloadEntry
      preloadEntry = null
      entry.cancelled = true
      entry.promise.then((asset) => disposeAsset(asset)).catch(() => {})
    }

    const preloadNext = (index) => {
      const items = enabledModels.value
      if (disposed || items.length < 2) return
      const nextIndex = (index + 1) % items.length
      const item = items[nextIndex]
      if (preloadEntry?.index === nextIndex && preloadEntry.url === item.modelUrl) return
      discardPreload()
      const entry = { index: nextIndex, url: item.modelUrl, cancelled: false, promise: null }
      entry.promise = prepareAsset(item)
        .then((asset) => {
          if (disposed || entry.cancelled) {
            disposeAsset(asset)
            return null
          }
          return asset
        })
        .catch((error) => {
          if (!entry.cancelled && !disposed) console.warn('3D model preload failed', error)
          return null
        })
      preloadEntry = entry
    }

    const clearCurrentModel = () => {
      finishTransition(false)
      disposeAsset(currentAsset)
      currentAsset = null
      discardPreload()
    }

    selectModel = async (index) => {
      const items = enabledModels.value
      if (!items.length) {
        ready.value = false
        failed.value = true
        return
      }
      const normalizedIndex = Math.min(Math.max(index, 0), items.length - 1)
      const item = items[normalizedIndex]
      if (ready.value && normalizedIndex === activeIndex.value && !failed.value) return
      requestedIndex.value = normalizedIndex
      switching.value = true
      failed.value = false
      carouselElapsed = 0
      const revision = ++loadRevision
      try {
        let asset
        if (preloadEntry?.index === normalizedIndex && preloadEntry.url === item.modelUrl) {
          const entry = preloadEntry
          preloadEntry = null
          asset = await entry.promise
          if (!asset) asset = await prepareAsset(item)
        } else {
          discardPreload()
          asset = await prepareAsset(item)
        }
        if (disposed || revision !== loadRevision) {
          disposeAsset(asset)
          return
        }
        finishTransition(false)
        switching.value = true
        scene.add(asset.root)
        startAssetAnimation(asset)
        if (!currentAsset || motion.matches) {
          disposeAsset(currentAsset)
          setAssetOpacity(asset, 1, true)
          currentAsset = asset
          switching.value = false
        } else {
          setAssetOpacity(asset, 0)
          asset.root.position.x = 0.08
          transition = { from: currentAsset, to: asset, elapsed: 0, duration: 0.45 }
          currentAsset = asset
        }
        activeIndex.value = normalizedIndex
        renderer.domElement.setAttribute('aria-label', `可拖动旋转的 ${item.title} 三维模型`)
        host.dataset.modelUrl = item.modelUrl
        host.dataset.animationClips = String(asset.animations.length)
        animationElapsed = 0
        ready.value = true
        if (!transition) preloadNext(normalizedIndex)
      } catch (error) {
        if (revision !== loadRevision || disposed) return
        console.error('3D model loading failed', error)
        failed.value = true
        switching.value = false
      }
    }

    const modelSignature = () =>
      enabledModels.value.map((item) => `${item.modelUrl}|${item.title}|${item.description}`).join('||')
    const stopModelWatch = watch(modelSignature, () => selectModel(0))

    const animate = (now) => {
      frame = requestAnimationFrame(animate)
      const delta = Math.min((now - lastFrame) / 1000, 0.05)
      lastFrame = now
      if (!visible || document.hidden) return
      if (transition) {
        if (motion.matches) {
          finishTransition()
        } else {
          transition.elapsed += delta
          const progress = Math.min(transition.elapsed / transition.duration, 1)
          const eased = 1 - Math.pow(1 - progress, 3)
          setAssetOpacity(transition.from, 1 - eased)
          setAssetOpacity(transition.to, eased)
          transition.from.root.position.x = -0.08 * eased
          transition.to.root.position.x = 0.08 * (1 - eased)
          if (progress >= 1) finishTransition()
        }
      }
      if (!paused.value) {
        animationElapsed += delta
        currentAsset?.mixer?.update(delta)
        if (currentAsset) {
          currentAsset.root.rotation.y += delta * 0.12
          currentAsset.root.position.y = 0.04 + Math.sin(animationElapsed * 1.4) * 0.012
        }
        if (!interactionHeld.value && ready.value && !switching.value && enabledModels.value.length > 1) {
          carouselElapsed += delta
          if (carouselElapsed >= 8) selectModel((activeIndex.value + 1) % enabledModels.value.length)
        }
      }
      controls.update()
      renderer.render(scene, camera)
    }

    const onContextLost = (event) => {
      event.preventDefault()
      failed.value = true
      ready.value = false
      cancelAnimationFrame(frame)
    }
    renderer.domElement.addEventListener('webglcontextlost', onContextLost)

    teardown = () => {
      loadRevision += 1
      cancelAnimationFrame(frame)
      stopModelWatch()
      resizeObserver.disconnect()
      intersectionObserver.disconnect()
      motion.removeEventListener('change', onMotion)
      renderer.domElement.removeEventListener('keydown', keyboard)
      renderer.domElement.removeEventListener('webglcontextlost', onContextLost)
      controls.dispose()
      clearCurrentModel()
      environment.dispose()
      floor.geometry.dispose()
      floor.material.dispose()
      renderer.dispose()
      renderer.forceContextLoss()
      renderer.domElement.remove()
    }

    frame = requestAnimationFrame(animate)
    await selectModel(0)
  } catch (error) {
    console.error('3D scene loading failed', error)
    teardown()
    if (!disposed) failed.value = true
  }
})

onBeforeUnmount(() => {
  disposed = true
  teardown()
})
</script>

<template>
  <figure
    class="research-visual product-scene"
    @mouseenter="interactionHeld = true"
    @mouseleave="interactionHeld = false"
    @focusin="interactionHeld = true"
    @focusout="releaseFocusPause"
  >
    <header class="product-scene-heading">
      <span><i /> {{ fullName || 'AIR × GROUND' }}</span>
      <span>{{ displayName || '空地协同' }}</span>
    </header>
    <div class="product-scene-stage" :aria-busy="switching">
      <div ref="mount" class="product-scene-canvas" :class="{ 'is-ready': ready }" />
      <div v-if="!ready" class="product-scene-status" role="status" aria-live="polite">
        <span v-if="!failed" class="model-loading-dot" />
        <div>
          <p>{{ failed ? '当前 3D 模型加载失败' : '正在加载三维模型…' }}</p>
          <button v-if="failed" type="button" @click="retryModel">
            <RotateCw :size="15" aria-hidden="true" />重新加载
          </button>
        </div>
      </div>
      <div v-else-if="switching || failed" class="product-scene-switch-status" role="status" aria-live="polite">
        <span v-if="switching" class="model-loading-dot" />
        <span>{{ failed ? '下一个模型加载失败' : '正在准备下一个模型' }}</span>
        <button v-if="failed" type="button" @click="retryModel"><RotateCw :size="14" aria-hidden="true" />重试</button>
      </div>
      <div v-if="activeModel" class="product-scene-caption" aria-live="off">
        <span v-if="hasMultipleModels">MODEL {{ activeIndex + 1 }} / {{ enabledModels.length }}</span>
        <strong>{{ activeModel.title }}</strong>
        <small>{{ activeModel.description }}</small>
      </div>
    </div>
    <div class="product-scene-footer">
      <span><Move :size="14" aria-hidden="true" />拖动查看</span>
      <div class="product-scene-controls" aria-label="3D 模型轮播控制">
        <button
          type="button"
          :disabled="!hasMultipleModels || switching"
          aria-label="上一个 3D 模型"
          @click="changeSlide(-1)"
        >
          <ChevronLeft :size="17" aria-hidden="true" />
        </button>
        <div v-if="hasMultipleModels" class="product-scene-dots" aria-label="选择 3D 模型">
          <button
            v-for="(item, index) in enabledModels"
            :key="item.modelUrl"
            type="button"
            :class="{ active: index === activeIndex }"
            :disabled="switching"
            :aria-label="`显示 ${item.title}`"
            :aria-current="index === activeIndex ? 'true' : undefined"
            @click="selectModel(index)"
          >
            <span />
          </button>
        </div>
        <button
          type="button"
          :disabled="!hasMultipleModels || switching"
          aria-label="下一个 3D 模型"
          @click="changeSlide(1)"
        >
          <ChevronRight :size="17" aria-hidden="true" />
        </button>
        <button
          type="button"
          :disabled="!ready"
          :aria-label="paused ? '播放模型动画和自动轮播' : '暂停模型动画和自动轮播'"
          :aria-pressed="paused"
          @click="paused = !paused"
        >
          <Play v-if="paused" :size="16" aria-hidden="true" />
          <Pause v-else :size="16" aria-hidden="true" />
        </button>
        <button type="button" :disabled="!ready" aria-label="重置模型视角" @click="resetView()">
          <RotateCcw :size="16" aria-hidden="true" />
        </button>
      </div>
    </div>
  </figure>
</template>

<style scoped>
.product-scene {
  max-width: 640px;
  padding: 0;
  isolation: isolate;
}
.product-scene-heading {
  display: flex;
  justify-content: space-between;
  padding: 10px 20px;
  color: var(--color-muted-foreground);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.product-scene-heading > span:first-child {
  display: flex;
  gap: 9px;
  align-items: center;
}
.product-scene-heading i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #55b8ac;
}
.product-scene-stage {
  position: relative;
  aspect-ratio: 1.15;
  min-height: 300px;
  background: radial-gradient(ellipse at 52% 50%, rgba(120, 162, 192, 0.09), transparent 67%);
}
.product-scene-canvas {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.35s ease;
  cursor: grab;
}
.product-scene-canvas:active {
  cursor: grabbing;
}
.product-scene-canvas.is-ready {
  opacity: 1;
}
.product-scene-canvas :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
  touch-action: pan-y;
}
.product-scene-status {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  color: var(--color-muted-foreground);
  font-size: 14px;
  text-align: center;
}
.product-scene-status p {
  margin: 0;
}
.product-scene-status button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  margin-top: 8px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-primary);
  background: var(--color-card);
  cursor: pointer;
}
.product-scene-switch-status {
  position: absolute;
  z-index: 2;
  top: 12px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 7px 10px;
  border: 1px solid var(--color-border-faint);
  border-radius: 999px;
  color: var(--color-muted-foreground);
  background: color-mix(in srgb, var(--color-card) 88%, transparent);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(8px);
  font-size: 11px;
}
.product-scene-switch-status button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 28px;
  padding: 3px 7px;
  border: 0;
  border-radius: 999px;
  color: var(--color-primary);
  background: var(--color-surface-muted);
  cursor: pointer;
}
.model-loading-dot {
  width: 8px;
  height: 8px;
  background: #62b9ac;
  border-radius: 50%;
  animation: model-loading-pulse 1s ease-in-out infinite alternate;
}
.product-scene-caption {
  position: absolute;
  bottom: 18px;
  left: 24px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  max-width: min(70%, 360px);
  pointer-events: none;
}
.product-scene-caption > span {
  color: var(--color-accent);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.1em;
}
.product-scene-caption strong {
  color: var(--color-primary);
  font-size: 20px;
  font-weight: 500;
  letter-spacing: -0.035em;
}
.product-scene-caption small {
  color: var(--color-muted-foreground);
  font-size: 12px;
  line-height: 1.45;
}
.product-scene-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin: 0 20px;
  padding: 8px 0;
  border-top: 1px solid var(--color-border-faint);
  color: var(--color-muted-foreground);
}
.product-scene-footer > span {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}
.product-scene-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  min-width: 0;
}
.product-scene-controls > button,
.product-scene-dots button {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  color: inherit;
  background: transparent;
  cursor: pointer;
  transition:
    color 0.18s ease,
    background 0.18s ease;
}
.product-scene-controls button:hover {
  color: var(--color-primary);
  background: var(--color-surface-muted);
}
.product-scene-controls button:focus-visible,
.product-scene-status button:focus-visible,
.product-scene-switch-status button:focus-visible {
  outline: 2px solid var(--color-secondary);
  outline-offset: 2px;
}
.product-scene-controls button:disabled {
  opacity: 0.35;
  cursor: default;
}
.product-scene-dots {
  display: flex;
  align-items: center;
}
.product-scene-dots button {
  width: 44px;
}
.product-scene-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-border);
  transition:
    width 0.18s ease,
    background 0.18s ease;
}
.product-scene-dots button.active span {
  width: 16px;
  border-radius: 999px;
  background: var(--color-primary);
}
@keyframes model-loading-pulse {
  to {
    opacity: 0.35;
    transform: scale(0.8);
  }
}
@media (max-width: 620px) {
  .product-scene-stage {
    min-height: 280px;
  }
  .product-scene-heading {
    padding-inline: 4px;
  }
  .product-scene-caption {
    bottom: 8px;
    left: 8px;
  }
  .product-scene-footer {
    align-items: flex-start;
    flex-direction: column;
    margin-inline: 4px;
  }
  .product-scene-controls {
    width: 100%;
    justify-content: space-between;
  }
  .product-scene-dots {
    overflow-x: auto;
    max-width: 156px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .product-scene-canvas,
  .product-scene-controls button,
  .product-scene-dots span {
    transition: none;
  }
  .model-loading-dot {
    animation: none;
  }
}
</style>
