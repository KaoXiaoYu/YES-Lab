<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { RotateCcw, Pause, Play, Move } from 'lucide-vue-next'
defineProps({ fullName: String })
const mount = ref(null)
const ready = ref(false)
const failed = ref(false)
const paused = ref(false)
let teardown = () => {}, resetView = () => {}
let disposed = false
onMounted(async () => {
  try {
    const [T, { GLTFLoader }, { OrbitControls }, { RoomEnvironment }] = await Promise.all([
      import('three'), import('three/addons/loaders/GLTFLoader.js'),
      import('three/addons/controls/OrbitControls.js'), import('three/addons/environments/RoomEnvironment.js'),
    ])
    if (disposed) return
    const host = mount.value
    const scene = new T.Scene()
    const camera = new T.PerspectiveCamera(34, 1, .01, 30)
    const renderer = new T.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' })
    renderer.setPixelRatio(Math.min(devicePixelRatio, 1.75))
    renderer.setClearColor(0x000000, 0)
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = T.PCFSoftShadowMap
    renderer.toneMapping = T.ACESFilmicToneMapping
    renderer.toneMappingExposure = .85
    renderer.domElement.setAttribute('aria-label', '可拖动旋转的 Go2 与无人机三维模型')
    renderer.domElement.setAttribute('role', 'img')
    renderer.domElement.tabIndex = 0
    renderer.domElement.setAttribute('aria-description', '拖动或使用方向键旋转，Home 键重置视角')
    host.appendChild(renderer.domElement)
    const controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.enableZoom = false
    controls.enablePan = false
    controls.minPolarAngle = .55
    controls.maxPolarAngle = Math.PI / 2 - .05
    controls.rotateSpeed = .55
    resetView = () => { camera.position.set(1.15, 1.2, 2.05); controls.target.set(0, .48, 0); controls.update() }
    resetView()
    const keyboard = e => {
      if (e.key === 'Home') { e.preventDefault(); resetView(); return }
      if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(e.key)) return
      e.preventDefault()
      const spherical = new T.Spherical().setFromVector3(camera.position.clone().sub(controls.target))
      spherical.theta += e.key === 'ArrowLeft' ? -.12 : e.key === 'ArrowRight' ? .12 : 0
      spherical.phi = T.MathUtils.clamp(spherical.phi + (e.key === 'ArrowUp' ? -.1 : e.key === 'ArrowDown' ? .1 : 0), controls.minPolarAngle, controls.maxPolarAngle)
      camera.position.copy(controls.target).add(new T.Vector3().setFromSpherical(spherical)); controls.update()
    }
    renderer.domElement.addEventListener('keydown', keyboard)
    const pmrem = new T.PMREMGenerator(renderer)
    const room = new RoomEnvironment()
    const environment = pmrem.fromScene(room, .04)
    scene.environment = environment.texture
    room.dispose(); pmrem.dispose()
    const ambient = new T.HemisphereLight(0xddefff, 0x556071, .8)
    scene.add(ambient)
    const key = new T.DirectionalLight(0xfff8ed, 2.2)
    key.position.set(-1, 3, 2); key.castShadow = true
    key.shadow.mapSize.set(1024, 1024)
    key.shadow.camera.left = -1.6; key.shadow.camera.right = 1.6
    key.shadow.camera.top = 1.6; key.shadow.camera.bottom = -1.6
    key.shadow.normalBias = .015
    key.shadow.bias = -.0002
    scene.add(key)
    const rim = new T.DirectionalLight(0x8fcaff, 1.4)
    rim.position.set(1, 1.5, -2); scene.add(rim)
    const floor = new T.Mesh(new T.PlaneGeometry(200, 200), new T.ShadowMaterial({ color: 0x193656, opacity: .16 }))
    floor.rotation.x = -Math.PI / 2; floor.position.y = -.005; floor.receiveShadow = true
    scene.add(floor)
    const resize = () => { if (!host.clientWidth) return; camera.aspect = host.clientWidth / host.clientHeight; camera.updateProjectionMatrix(); renderer.setSize(host.clientWidth, host.clientHeight, false) }
    const observer = new ResizeObserver(resize); observer.observe(host); resize()
    const motion = matchMedia('(prefers-reduced-motion: reduce)')
    paused.value = motion.matches
    const onMotion = () => { paused.value = motion.matches }
    motion.addEventListener('change', onMotion)
    let visible = true
    const intersection = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting }); intersection.observe(host)
    let dog, drone, last = 0, elapsed = 0, frame
    const animate = (now) => {
      frame = requestAnimationFrame(animate)
      const dt = Math.min((now - last) / 1000, .05); last = now
      if (!visible || document.hidden) return
      if (!paused.value) elapsed += dt
      if (drone) {
        drone.position.y = .76 + Math.sin(elapsed * 1.6) * .018
        drone.rotation.z = Math.sin(elapsed * 1.1) * .015
      }
      controls.update()
      renderer.render(scene, camera)
    }
    const onContextLost = e => { e.preventDefault(); failed.value = true; ready.value = false; cancelAnimationFrame(frame) }
    renderer.domElement.addEventListener('webglcontextlost', onContextLost)
    teardown = () => {
      cancelAnimationFrame(frame); observer.disconnect(); intersection.disconnect(); motion.removeEventListener('change', onMotion)
      renderer.domElement.removeEventListener('keydown', keyboard); controls.dispose(); environment.dispose()
      const geometries = new Set(), materials = new Set(), textures = new Set()
      scene.traverse(o => { if (o.geometry) geometries.add(o.geometry); if (o.material) (Array.isArray(o.material) ? o.material : [o.material]).forEach(m => materials.add(m)) })
      materials.forEach(m => { Object.values(m).forEach(v => { if (v?.isTexture) textures.add(v) }); m.dispose() })
      textures.forEach(t => t.dispose()); geometries.forEach(g => g.dispose()); renderer.dispose(); renderer.domElement.remove()
    }
    const loader = new GLTFLoader()
    const [dogAsset, droneAsset] = await Promise.all([loader.loadAsync('/models/go2.glb'), loader.loadAsync('/models/skydio-x2.glb')])
    if (disposed) { for (const asset of [dogAsset, droneAsset]) asset.scene.traverse(o => { o.geometry?.dispose(); o.material?.map?.dispose(); o.material?.dispose() }); return }
    dog = dogAsset.scene; drone = droneAsset.scene
    dog.position.set(.19, 0, .14); dog.rotation.y = -.22
    drone.position.set(-.28, .76, -.12); drone.rotation.y = .25
    drone.scale.setScalar(1.15)
    // The source asset already uses motion-blurred rotor discs. Rotating the
    // separated mesh nodes caused two discs to orbit their imported pivots.
    // Keep all four rotor assemblies fixed to their motors and animate only
    // the aircraft's subtle hover motion.
    for (const object of [dog, drone]) { object.traverse(o => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; if (o.material) o.material.envMapIntensity = .85 } }); scene.add(object) }
    host.dataset.models = 'go2,skydio-x2'
    host.dataset.rotorMotion = 'fixed-to-motors'
    ready.value = true
    frame = requestAnimationFrame(animate)
  } catch (error) { console.error('3D scene loading failed', error); teardown(); if (!disposed) failed.value = true }
})
onBeforeUnmount(() => { disposed = true; teardown() })
</script>

<template>
  <figure class="research-visual product-scene">
    <header class="product-scene-heading"><span><i /> AIR × GROUND</span><span>空地协同</span></header>
    <div class="product-scene-stage">
      <div ref="mount" class="product-scene-canvas" :class="{ 'is-ready': ready }" />
      <div v-if="!ready" class="product-scene-status" role="status">
        <span v-if="!failed" class="model-loading-dot" />
        <p>{{ failed ? '3D 展示暂未加载，请刷新页面重试' : '正在加载三维模型…' }}</p>
      </div>
      <div v-if="ready" class="product-scene-caption"><strong>Unitree Go2</strong><span>四足机器人 × 自主飞行</span></div>
    </div>
    <div class="product-scene-footer">
      <span><Move :size="14" /> 拖动查看</span>
      <div><button type="button" :disabled="!ready" :aria-label="paused ? '播放模型动画' : '暂停模型动画'" :aria-pressed="paused" @click="paused = !paused"><Play v-if="paused" :size="16" /><Pause v-else :size="16" /></button><button type="button" :disabled="!ready" aria-label="重置模型视角" @click="resetView()"><RotateCcw :size="16" /></button></div>
    </div>
  </figure>
</template>

<style scoped>
.product-scene { max-width: 640px; padding: 0; isolation: isolate; }
.product-scene-heading { display: flex; justify-content: space-between; padding: 10px 20px; color: var(--color-muted-foreground); font-size: 12px; letter-spacing: .08em; }
.product-scene-heading > span:first-child { display: flex; gap: 9px; align-items: center; }
.product-scene-heading i { width: 5px; height: 5px; border-radius: 50%; background: #55b8ac; }
.product-scene-stage { position: relative; aspect-ratio: 1.15; min-height: 300px; background: radial-gradient(ellipse at 52% 50%, rgba(120,162,192,.09), transparent 67%); }
.product-scene-canvas { position: absolute; inset: 0; opacity: 0; transition: opacity .5s ease; cursor: grab; }
.product-scene-canvas:active { cursor: grabbing; }
.product-scene-canvas.is-ready { opacity: 1; }
.product-scene-canvas :deep(canvas) { display: block; width: 100%; height: 100%; touch-action: pan-y; }
.product-scene-status { position: absolute; inset: 0; display: flex; justify-content: center; align-items: center; gap: 12px; color: var(--color-muted-foreground); font-size: 14px; }
.model-loading-dot { width: 8px; height: 8px; background: #62b9ac; border-radius: 50%; }
.product-scene-caption { position: absolute; bottom: 22px; left: 24px; display: flex; flex-direction: column; gap: 6px; pointer-events: none; }
.product-scene-caption strong { font-size: 20px; font-weight: 500; letter-spacing: -.035em; color: var(--color-primary); }
.product-scene-caption span { font-size: 12px; color: var(--color-muted-foreground); }
.product-scene-footer { display: flex; justify-content: space-between; align-items: center; margin: 0 20px; padding: 10px 0; border-top: 1px solid var(--color-border-faint); color: var(--color-muted-foreground); }
.product-scene-footer > span { display: flex; gap: 8px; align-items: center; font-size: 12px; }
.product-scene-footer > div { display: flex; gap: 4px; }
.product-scene-footer button { width: 38px; height: 38px; display: grid; place-items: center; background: transparent; color: inherit; border: 0; border-radius: 50%; cursor: pointer; }
.product-scene-footer button:hover { background: var(--color-surface-muted); }
.product-scene-footer button:focus-visible { outline: 2px solid var(--color-secondary); outline-offset: 2px; }
.product-scene-footer button:disabled { opacity: .4; cursor: default; }
@media (max-width: 430px) { .product-scene-stage { min-height: 280px; } .product-scene-heading { padding-inline: 4px; } .product-scene-caption { left: 8px; bottom: 8px; } .product-scene-footer { margin-inline: 4px; } }
@media (prefers-reduced-motion: reduce) { .product-scene-canvas { transition: none; } }
</style>
