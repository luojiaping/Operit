package com.ai.assistance.operit.data.theme.packages

import com.ai.assistance.operit.ui.theme.scene.ThemeSceneDefinitionV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneNodeV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenSetV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenValueV1
import java.io.File

internal class ThemePackageLinkExceptionV2(message: String) : IllegalStateException(message)

/**
 * Immutable, fully linked visual contract. Compose roots consume this object and never inspect
 * package JSON, archive contents, or an unlinked base package while rendering.
 */
internal data class LinkedThemeRuntimeV2(
    val coordinate: ThemePackageCoordinateV2,
    val packageChain: List<ThemePackageCoordinateV2>,
    val material: ThemeMaterialProjectionV2,
    val componentSkins: Map<ThemeComponentIdV2, ThemeComponentSkinV2>,
    val surfaces: Map<ThemeSurfaceIdV2, ThemeSurfaceImplementationV2>,
    val tokens: ThemeSceneTokenSetV1,
    val scenes: Map<String, ThemeSceneDefinitionV1>,
    val assets: Map<String, File>,
    val behavior: ThemePackagePresentationBehaviorV2 = ThemePackagePresentationBehaviorV2(),
    val componentOwners: Map<ThemeComponentIdV2, ThemePackageCoordinateV2> = emptyMap(),
    val assetKinds: Map<String, ThemeAssetKindV2> = emptyMap(),
    val parameterDefinitions: Map<String, ThemeParameterDefinitionV2>,
    val parameterOwners: Map<String, ThemePackageCoordinateV2> = emptyMap(),
)

internal data class ResolvedThemeParametersV2(
    val values: Map<String, ThemeParameterValueV2>,
    val overriddenIds: Set<String> = emptySet(),
) {
    fun colorArgb(id: String): Long? = (values[id] as? ThemeParameterValueV2.ColorValue)?.argb

    fun imageUri(id: String): String? = (values[id] as? ThemeParameterValueV2.ImageUriValue)?.uri

    fun value(id: String): ThemeParameterValueV2? = values[id]

    fun isOverridden(id: String): Boolean = id in overriddenIds

    fun isUserVisible(definition: ThemeParameterDefinitionV2): Boolean =
        definition.visibility == ThemeParameterVisibilityV2.USER &&
            definition.visibleWhen.all { condition ->
                when (condition) {
                    is ThemeParameterConditionV2.BooleanEquals ->
                        (values[condition.parameterId] as? ThemeParameterValueV2.BooleanValue)?.value ==
                            condition.expected

                    is ThemeParameterConditionV2.OptionEquals ->
                        (values[condition.parameterId] as? ThemeParameterValueV2.OptionValue)?.value ==
                            condition.expected

                    is ThemeParameterConditionV2.ResourcePresent ->
                        values[condition.parameterId]?.isResourceUriValue() == true
                }
            }
}

private fun ThemeParameterValueV2.isResourceUriValue(): Boolean =
    this is ThemeParameterValueV2.ImageUriValue ||
        this is ThemeParameterValueV2.VideoUriValue ||
        this is ThemeParameterValueV2.FontUriValue

internal object ThemePackageRuntimeLinkerV2 {
    fun link(
        active: PublishedThemeInstallationV2,
        catalog: PublishedThemeCatalogV2,
    ): LinkedThemeRuntimeV2 {
        val installations = catalog.installations.associateBy { installation -> installation.coordinate }
        val chain = resolveChain(active, installations, linkedSetOf())
        val merged = chain.fold(LinkedThemeAccumulatorV2()) { state, installation ->
            state.merge(installation)
        }
        return merged.toRuntime(active.coordinate, chain.map { installation -> installation.coordinate })
    }

    fun resolveParameters(
        instance: ThemeInstanceV2,
        runtime: LinkedThemeRuntimeV2,
    ): ResolvedThemeParametersV2 {
        val values = LinkedHashMap<String, ThemeParameterValueV2>()
        runtime.parameterDefinitions.forEach { (id, definition) ->
            if (runtime.parameterOwners[id] != runtime.coordinate) {
                return@forEach
            }
            val value = instance.parameterValues[id] ?: definition.defaultValue
            if (value != null) {
                if (!value.matches(definition.type)) {
                    throw ThemePackageLinkExceptionV2(
                        "Theme parameter $id does not match declared type ${definition.type}.",
                    )
                }
                validateParameterValue(id, definition, value)
                values[id] = value
            }
        }
        instance.parameterValues.keys.forEach { id ->
            if (id !in runtime.parameterDefinitions) {
                throw ThemePackageLinkExceptionV2(
                    "Theme instance declares unknown parameter: $id",
                )
            }
            if (runtime.parameterOwners[id] != runtime.coordinate) {
                throw ThemePackageLinkExceptionV2(
                    "Theme instance cannot override inherited parameter: $id",
                )
            }
        }
        return ResolvedThemeParametersV2(
            values = values,
            overriddenIds = instance.parameterValues.keys,
        )
    }

    private fun validateParameterValue(
        id: String,
        definition: ThemeParameterDefinitionV2,
        value: ThemeParameterValueV2,
    ) {
        if (!value.matches(definition.type)) {
            throw ThemePackageLinkExceptionV2(
                "Theme parameter $id does not match declared type ${definition.type}.",
            )
        }
        when (val control = definition.control) {
            is ThemeParameterControlV2.ColorPalette -> {
                val color = value as? ThemeParameterValueV2.ColorValue
                    ?: throw ThemePackageLinkExceptionV2("Theme color parameter $id must resolve to a color.")
                if (color.argb ushr 24 != 0xFFL) {
                    throw ThemePackageLinkExceptionV2(
                        "Theme color parameter $id must be opaque.",
                    )
                }
            }

            is ThemeParameterControlV2.Choice -> {
                val option = value as? ThemeParameterValueV2.OptionValue
                    ?: throw ThemePackageLinkExceptionV2("Theme option parameter $id must resolve to an option.")
                if (control.options.none { choice -> choice.id == option.value }) {
                    throw ThemePackageLinkExceptionV2(
                        "Theme parameter $id selects an undeclared option ${option.value}.",
                    )
                }
            }

            is ThemeParameterControlV2.Slider -> {
                val numeric = value as? ThemeParameterValueV2.FloatValue
                    ?: throw ThemePackageLinkExceptionV2("Theme slider parameter $id must resolve to a number.")
                if (numeric.value !in control.minimum..control.maximum) {
                    throw ThemePackageLinkExceptionV2(
                        "Theme parameter $id is outside its declared slider range.",
                    )
                }
            }

            else -> Unit
        }
    }

    private fun resolveChain(
        installation: PublishedThemeInstallationV2,
        installations: Map<ThemePackageCoordinateV2, PublishedThemeInstallationV2>,
        visiting: MutableSet<ThemePackageCoordinateV2>,
    ): List<PublishedThemeInstallationV2> {
        if (!visiting.add(installation.coordinate)) {
            throw ThemePackageLinkExceptionV2(
                "Theme package basis cycle includes ${installation.coordinate.packageId.value}.",
            )
        }
        val result = buildList {
            installation.manifest.basis?.let { basis ->
                val base = installations[basis]
                    ?: throw ThemePackageLinkExceptionV2(
                        "Theme package basis is not installed: ${basis.packageId.value}@${basis.version.value}",
                    )
                addAll(resolveChain(base, installations, visiting))
            }
            add(installation)
        }
        visiting.remove(installation.coordinate)
        return result
    }
}

private data class LinkedThemeParameterV2(
    val definition: ThemeParameterDefinitionV2,
    val owner: ThemePackageCoordinateV2,
)

private data class LinkedThemeAccumulatorV2(
    val material: ThemeMaterialProjectionV2? = null,
    val materialOwner: ThemePackageCoordinateV2? = null,
    val behavior: ThemePackagePresentationBehaviorV2? = null,
    val componentSkins: Map<String, ThemeComponentSkinV2> = emptyMap(),
    val componentOwners: Map<String, ThemePackageCoordinateV2> = emptyMap(),
    val surfaces: Map<String, ThemeSurfaceImplementationV2> = emptyMap(),
    val surfaceOwners: Map<String, ThemePackageCoordinateV2> = emptyMap(),
    val tokens: Map<String, ThemeSceneTokenValueV1> = emptyMap(),
    val tokenOwners: Map<String, ThemePackageCoordinateV2> = emptyMap(),
    val scenes: Map<String, ThemeSceneDefinitionV1> = emptyMap(),
    val sceneOwners: Map<String, ThemePackageCoordinateV2> = emptyMap(),
    val assets: Map<String, File> = emptyMap(),
    val assetKinds: Map<String, ThemeAssetKindV2> = emptyMap(),
    val parameters: Map<String, LinkedThemeParameterV2> = emptyMap(),
) {
    fun merge(installation: PublishedThemeInstallationV2): LinkedThemeAccumulatorV2 {
        val manifest = installation.manifest
        val declaredSurfaces = manifest.surfaces.associateBy { surface -> surface.surfaceId }
        val declaredTokens = manifest.tokens.tokens
        val declaredScenes = manifest.scenes.associateBy { scene -> scene.sceneId.value }
        val duplicateAssetKeys = assets.keys.intersect(manifest.assets.map { asset -> asset.key }.toSet())
        if (duplicateAssetKeys.isNotEmpty()) {
            throw ThemePackageLinkExceptionV2(
                "Theme package ${manifest.packageId} redefines inherited asset keys: " +
                    duplicateAssetKeys.sorted().joinToString(),
            )
        }
        val duplicateParameterIds = parameters.keys.intersect(manifest.parameters.map { parameter -> parameter.id }.toSet())
        if (duplicateParameterIds.isNotEmpty()) {
            throw ThemePackageLinkExceptionV2(
                "Theme package ${manifest.packageId} redefines inherited parameters: " +
                    duplicateParameterIds.sorted().joinToString(),
            )
        }
        return copy(
            material = manifest.presentation.material ?: material,
            materialOwner =
                if (manifest.presentation.material != null) installation.coordinate else materialOwner,
            behavior = manifest.presentation.behavior,
            componentSkins = componentSkins + manifest.presentation.componentSkins,
            componentOwners =
                componentOwners + manifest.presentation.componentSkins.keys.associateWith { installation.coordinate },
            surfaces = surfaces + declaredSurfaces,
            surfaceOwners =
                surfaceOwners + declaredSurfaces.keys.associateWith { installation.coordinate },
            tokens = tokens + declaredTokens,
            tokenOwners =
                tokenOwners + declaredTokens.keys.associateWith { installation.coordinate },
            scenes = scenes + declaredScenes,
            sceneOwners =
                sceneOwners + declaredScenes.keys.associateWith { installation.coordinate },
            assets =
                assets +
                    manifest.assets.associate { asset ->
                        asset.key to File(installation.rootDir, asset.path)
                    },
            assetKinds = assetKinds + manifest.assets.associate { asset -> asset.key to asset.kind },
            parameters =
                parameters +
                    manifest.parameters.associate { parameter ->
                        parameter.id to
                            LinkedThemeParameterV2(
                                definition = parameter,
                                owner = installation.coordinate,
                            )
                    },
        )
    }

    fun toRuntime(
        coordinate: ThemePackageCoordinateV2,
        packageChain: List<ThemePackageCoordinateV2>,
    ): LinkedThemeRuntimeV2 {
        val resolvedMaterial = material
            ?: throw ThemePackageLinkExceptionV2("Theme package has no linked Material projection.")
        val resolvedMaterialOwner = materialOwner
            ?: throw ThemePackageLinkExceptionV2("Theme package has no Material projection owner.")
        val resolvedBehavior = requireNotNull(behavior) {
            "Theme package has no linked presentation behavior."
        }
        val componentSkinMap = componentSkins.mapKeys { (id, _) -> ThemeComponentIdV2(id) }
        val componentOwnerMap = componentOwners.mapKeys { (id, _) -> ThemeComponentIdV2(id) }
        val surfaceMap = surfaces.mapKeys { (id, _) -> ThemeSurfaceIdV2(id) }
        validateCoverage(componentSkinMap, surfaceMap)
        validateTokenReferences(resolvedMaterial, componentSkinMap, tokens)
        validateSceneReferences(surfaces, scenes, tokens, assets, assetKinds)
        validateParameterEffects(
            activeCoordinate = coordinate,
            parameters = parameters,
            material = resolvedMaterial,
            materialOwner = resolvedMaterialOwner,
            componentOwners = componentOwnerMap,
            surfaces = surfaceMap,
            surfaceOwners = surfaceOwners,
            scenes = scenes,
            sceneOwners = sceneOwners,
            tokens = tokens,
            tokenOwners = tokenOwners,
        )
        return LinkedThemeRuntimeV2(
            coordinate = coordinate,
            packageChain = packageChain,
            material = resolvedMaterial,
            behavior = resolvedBehavior,
            componentSkins = componentSkinMap,
            componentOwners = componentOwnerMap,
            surfaces = surfaceMap,
            tokens = ThemeSceneTokenSetV1(tokens),
            scenes = scenes,
            assets = assets,
            assetKinds = assetKinds,
            parameterDefinitions = parameters.mapValues { (_, parameter) -> parameter.definition },
            parameterOwners = parameters.mapValues { (_, parameter) -> parameter.owner },
        )
    }

    private fun validateCoverage(
        componentSkins: Map<ThemeComponentIdV2, ThemeComponentSkinV2>,
        surfaces: Map<ThemeSurfaceIdV2, ThemeSurfaceImplementationV2>,
    ) {
        val missingSurfaces = ThemeSurfaceCatalogV2.requiredDailySurfaces - surfaces.keys
        if (missingSurfaces.isNotEmpty()) {
            throw ThemePackageLinkExceptionV2(
                "Theme package does not cover required daily surfaces: " +
                    missingSurfaces.map { surface -> surface.value }.sorted().joinToString(),
            )
        }
        val missingComponents = ThemeComponentCatalogV2.requiredComponents - componentSkins.keys
        if (missingComponents.isNotEmpty()) {
            throw ThemePackageLinkExceptionV2(
                "Theme package does not provide required component skins: " +
                    missingComponents.map { component -> component.value }.sorted().joinToString(),
            )
        }
        componentSkins.forEach { (component, skin) ->
            val missingStates = ThemeComponentCatalogV2.missingRequiredStateNames(component, skin)
            if (missingStates.isNotEmpty()) {
                throw ThemePackageLinkExceptionV2(
                    "Theme package component ${component.value} is missing required states: " +
                        missingStates.joinToString(),
                )
            }
        }
        surfaces.forEach { (surface, implementation) ->
            try {
                ThemeSurfaceHostPolicyV2.requireSupportedImplementation(surface, implementation)
            } catch (error: IllegalArgumentException) {
                throw ThemePackageLinkExceptionV2(error.message ?: "Invalid linked theme surface kind.")
            }
        }
    }

    private fun validateTokenReferences(
        material: ThemeMaterialProjectionV2,
        componentSkins: Map<ThemeComponentIdV2, ThemeComponentSkinV2>,
        tokens: Map<String, ThemeSceneTokenValueV1>,
    ) {
        material.colors.tokenIds().forEach { tokenId -> requireColor(tokens, tokenId, "Material projection") }
        componentSkins.forEach { (componentId, skin) ->
            listOfNotNull(skin.normal, skin.disabled, skin.selected, skin.focused, skin.error).forEach { state ->
                requireColor(tokens, state.containerToken, "Component ${componentId.value}")
                requireColor(tokens, state.contentToken, "Component ${componentId.value}")
                state.frame.strokes().forEach { stroke ->
                    requireColor(tokens, stroke.token, "Component ${componentId.value}")
                }
            }
        }
    }

    private fun validateSceneReferences(
        surfaces: Map<String, ThemeSurfaceImplementationV2>,
        scenes: Map<String, ThemeSceneDefinitionV1>,
        tokens: Map<String, ThemeSceneTokenValueV1>,
        assets: Map<String, File>,
        assetKinds: Map<String, ThemeAssetKindV2>,
    ) {
        surfaces.values.forEach { surface ->
            if (surface.kind == ThemeSurfaceImplementationKindV2.SCENE) {
                val sceneId = requireNotNull(surface.sceneId)
                if (sceneId !in scenes) {
                    throw ThemePackageLinkExceptionV2(
                        "Theme surface ${surface.surfaceId} references missing linked scene $sceneId.",
                    )
                }
            }
        }
        scenes.values.forEach { scene ->
            validateSceneNode(scene.rootNode, tokens, assets, assetKinds)
        }
    }

    private fun validateParameterEffects(
        activeCoordinate: ThemePackageCoordinateV2,
        parameters: Map<String, LinkedThemeParameterV2>,
        material: ThemeMaterialProjectionV2,
        materialOwner: ThemePackageCoordinateV2,
        componentOwners: Map<ThemeComponentIdV2, ThemePackageCoordinateV2>,
        surfaces: Map<ThemeSurfaceIdV2, ThemeSurfaceImplementationV2>,
        surfaceOwners: Map<String, ThemePackageCoordinateV2>,
        scenes: Map<String, ThemeSceneDefinitionV1>,
        sceneOwners: Map<String, ThemePackageCoordinateV2>,
        tokens: Map<String, ThemeSceneTokenValueV1>,
        tokenOwners: Map<String, ThemePackageCoordinateV2>,
    ) {
        parameters.values
            .filter { parameter -> parameter.owner == activeCoordinate }
            .groupBy { parameter -> parameter.owner }
            .forEach { (owner, ownedParameters) ->
                val colorTargets = mutableSetOf<String>()
                val stageTargets = mutableSetOf<ThemeSurfaceIdV2>()
                val componentFrameTargets = mutableSetOf<ThemeComponentIdV2>()
                val componentInsetTargets = mutableSetOf<ThemeComponentIdV2>()
                val presentationTargets = mutableSetOf<ThemePresentationTargetV2>()
                var accentPaletteDeclared = false
                ownedParameters.forEach { parameter ->
                    parameter.definition.effects.forEach { effect ->
                        when (effect) {
                            ThemeParameterEffectV2.AccentPalette -> {
                                if (materialOwner != owner) {
                                    throw ThemePackageLinkExceptionV2(
                                        "Theme package ${owner.packageId.value} cannot apply an accent palette to inherited Material roles.",
                                    )
                                }
                                if (accentPaletteDeclared) {
                                    throw ThemePackageLinkExceptionV2(
                                        "Theme package ${owner.packageId.value} declares multiple accent palette effects.",
                                    )
                                }
                                accentPaletteDeclared = true
                                val accentTokenIds = material.colors.accentTokenIds()
                                if (accentTokenIds.distinct().size != accentTokenIds.size) {
                                    throw ThemePackageLinkExceptionV2(
                                        "Theme accent palette requires distinct Material role tokens.",
                                    )
                                }
                                accentTokenIds.forEach { tokenId ->
                                    requireColor(tokens, tokenId, "Accent palette")
                                    requireOwnedTarget(tokenOwners[tokenId], owner, tokenId, "token")
                                    if (!colorTargets.add(tokenId)) {
                                        throw ThemePackageLinkExceptionV2(
                                            "Theme package ${owner.packageId.value} assigns multiple color effects to $tokenId.",
                                        )
                                    }
                                }
                            }

                            is ThemeParameterEffectV2.TokenColor ->
                                effect.tokenIds.forEach { tokenId ->
                                    requireColor(tokens, tokenId, "Theme parameter")
                                    requireOwnedTarget(tokenOwners[tokenId], owner, tokenId, "token")
                                    if (!colorTargets.add(tokenId)) {
                                        throw ThemePackageLinkExceptionV2(
                                            "Theme package ${owner.packageId.value} assigns multiple color effects to $tokenId.",
                                        )
                                    }
                                }

                            is ThemeParameterEffectV2.TokenColorPair ->
                                effect.tokenIds.forEach { tokenId ->
                                    requireColor(tokens, tokenId, "Theme parameter")
                                    requireOwnedTarget(tokenOwners[tokenId], owner, tokenId, "token")
                                    if (!colorTargets.add(tokenId)) {
                                        throw ThemePackageLinkExceptionV2(
                                            "Theme package ${owner.packageId.value} assigns multiple color effects to $tokenId.",
                                        )
                                    }
                                }

                            is ThemeParameterEffectV2.StageImage ->
                                effect.surfaceIds.forEach { surfaceId ->
                                    val surface = ThemeSurfaceIdV2(surfaceId)
                                    val implementation = surfaces[surface]
                                    if (implementation?.kind != ThemeSurfaceImplementationKindV2.SCENE) {
                                        throw ThemePackageLinkExceptionV2(
                                            "Theme stage image effect must target a scene surface: $surfaceId.",
                                        )
                                    }
                                    requireOwnedTarget(
                                        surfaceOwners[surfaceId],
                                        owner,
                                        surfaceId,
                                        "surface",
                                    )
                                    val sceneId = requireNotNull(implementation.sceneId)
                                    if (sceneId !in scenes) {
                                        throw ThemePackageLinkExceptionV2(
                                            "Theme stage image effect references missing scene $sceneId.",
                                        )
                                    }
                                    requireOwnedTarget(
                                        sceneOwners[sceneId],
                                        owner,
                                        sceneId,
                                        "scene",
                                    )
                                    if (!stageTargets.add(surface)) {
                                        throw ThemePackageLinkExceptionV2(
                                            "Theme package ${owner.packageId.value} assigns multiple stage images to $surfaceId.",
                                        )
                                    }
                                }

                            ThemeParameterEffectV2.TypographyScale,
                            ThemeParameterEffectV2.ShapeScale,
                            -> {
                                requireOwnedTarget(materialOwner, owner, "material", "Material projection")
                            }

                            is ThemeParameterEffectV2.ComponentFrameScale ->
                                effect.componentIds.forEach { componentId ->
                                    val component = ThemeComponentIdV2(componentId)
                                    requireOwnedTarget(
                                        componentOwners[component],
                                        owner,
                                        componentId,
                                        "component skin",
                                    )
                                    if (!componentFrameTargets.add(component)) {
                                        throw ThemePackageLinkExceptionV2(
                                            "Theme package ${owner.packageId.value} assigns multiple frame effects to $componentId.",
                                        )
                                    }
                                }

                            is ThemeParameterEffectV2.ComponentContentInsets ->
                                effect.componentIds.forEach { componentId ->
                                    val component = ThemeComponentIdV2(componentId)
                                    requireOwnedTarget(
                                        componentOwners[component],
                                        owner,
                                        componentId,
                                        "component skin",
                                    )
                                    if (!componentInsetTargets.add(component)) {
                                        throw ThemePackageLinkExceptionV2(
                                            "Theme package ${owner.packageId.value} assigns multiple inset effects to $componentId.",
                                        )
                                    }
                                }

                            is ThemeParameterEffectV2.Presentation ->
                                effect.targets.forEach { target ->
                                    if (!presentationTargets.add(target)) {
                                        throw ThemePackageLinkExceptionV2(
                                            "Theme package ${owner.packageId.value} assigns multiple presentation effects to $target.",
                                        )
                                    }
                                }
                        }
                    }
                }
            }
    }

    private fun requireOwnedTarget(
        targetOwner: ThemePackageCoordinateV2?,
        parameterOwner: ThemePackageCoordinateV2,
        target: String,
        targetKind: String,
    ) {
        if (targetOwner != parameterOwner) {
            throw ThemePackageLinkExceptionV2(
                "Theme package ${parameterOwner.packageId.value} cannot apply a parameter effect to inherited $targetKind $target.",
            )
        }
    }

    private fun validateSceneNode(
        node: ThemeSceneNodeV1,
        tokens: Map<String, ThemeSceneTokenValueV1>,
        assets: Map<String, File>,
        assetKinds: Map<String, ThemeAssetKindV2>,
    ) {
        when (node) {
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1 ->
                node.backgroundColorToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneSurfaceNodeV1 -> {
                node.fillToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }
                node.outlineToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }
            }

            is com.ai.assistance.operit.ui.theme.scene.ThemeScenePathNodeV1 -> {
                node.fillToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }
                node.outlineToken?.let { tokenId -> requireColor(tokens, tokenId.value, "Scene") }
                requireAsset(assets, assetKinds, node.assetId.value, ThemeAssetKindV2.PATH, "Scene")
            }

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneImageNodeV1 ->
                requireAsset(assets, assetKinds, node.assetId.value, ThemeAssetKindV2.BITMAP, "Scene")

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneNineSliceNodeV1 ->
                requireAsset(assets, assetKinds, node.assetId.value, ThemeAssetKindV2.NINE_SLICE, "Scene")

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTextNodeV1 -> {
                val styleToken = requireNotNull(node.styleToken) {
                    "Scene text node ${node.nodeId.value} requires a style token."
                }
                val style = tokens[styleToken.value] as? ThemeSceneTokenValueV1.TextStyleToken
                    ?: throw ThemePackageLinkExceptionV2(
                        "Scene references missing or non-text-style token: ${styleToken.value}",
                    )
                requireColor(tokens, style.color.value, "Scene text")
                style.fontAsset?.let { assetId ->
                    requireAsset(assets, assetKinds, assetId.value, ThemeAssetKindV2.FONT, "Scene text")
                }
            }

            else -> Unit
        }
        childrenOf(node).forEach { child -> validateSceneNode(child, tokens, assets, assetKinds) }
    }

    private fun requireColor(
        tokens: Map<String, ThemeSceneTokenValueV1>,
        tokenId: String,
        owner: String,
    ) {
        ThemeSceneTokenIdV1(tokenId)
        if (tokens[tokenId] !is ThemeSceneTokenValueV1.ColorToken) {
            throw ThemePackageLinkExceptionV2("$owner references missing or non-color token: $tokenId")
        }
    }

    private fun requireAsset(
        assets: Map<String, File>,
        assetKinds: Map<String, ThemeAssetKindV2>,
        assetId: String,
        expectedKind: ThemeAssetKindV2,
        owner: String,
    ) {
        if (assets[assetId]?.isFile != true) {
            throw ThemePackageLinkExceptionV2("$owner references missing linked asset: $assetId")
        }
        val actualKind = assetKinds[assetId]
        if (actualKind != expectedKind) {
            throw ThemePackageLinkExceptionV2(
                "$owner uses linked asset $assetId as $expectedKind but it is declared as $actualKind.",
            )
        }
    }

    private fun childrenOf(node: ThemeSceneNodeV1): List<ThemeSceneNodeV1> =
        when (node) {
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneStageNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneLayerNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneRowNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneColumnNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneScaffoldNodeV1 ->
                listOfNotNull(node.top, node.content, node.bottom, node.overlay)
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneGridNodeV1 -> node.children
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneFrameNodeV1 -> listOf(node.child)
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneTransformNodeV1 -> listOf(node.child)
            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneSurfaceNodeV1 ->
                node.child?.let(::listOf) ?: emptyList()

            is com.ai.assistance.operit.ui.theme.scene.ThemeSceneNineSliceNodeV1 ->
                node.child?.let(::listOf) ?: emptyList()

            else -> emptyList()
        }
}
