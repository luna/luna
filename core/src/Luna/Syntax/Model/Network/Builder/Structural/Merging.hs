{-# LANGUAGE CPP #-}

module Luna.Syntax.Model.Network.Builder.Structural.Merging where

import           Luna.Syntax.Model.Network.Builder.Term
import           Luna.Syntax.Model.Network.Builder.Layer
import           Prelude.Luna
import           Control.Monad                 (forM)
import           Data.Graph.Builder
import           Data.Graph.Backend.VectorGraph
import           Data.Container                (usedIxes)
import           Data.Container.SizeTracking   (SizeTracking)
import           Data.Layer.Cover
import           Data.Construction
import           Data.Index                    (idx)
import           Data.Prop
import           Data.Map                      (Map)
import           Data.Maybe                    (fromMaybe)
import           Data.List                     (partition)
import qualified Data.Map                      as Map
import qualified Data.IntSet                   as IntSet
import           Luna.Syntax.Term.Function      (Signature)

import           Luna.Syntax.Model.Layer
import           Luna.Syntax.Model.Network.Term (Draft)
import           Luna.Runtime.Dynamics        (Static)
import qualified Luna.Syntax.Term.Function       as Function

#define ImportCtx  ( node  ~ (ls :<: term)                            \
                   , edge  ~ Link node                                \
                   , graph ~ Hetero (VectorGraph n e c)               \
                   , BiCastable e edge                                \
                   , BiCastable n node                                \
                   , MonadBuilder graph m                             \
                   , Referred Node graph n                            \
                   , Covered node                                     \
                   , Constructor m (Ref Node node)                    \
                   , Constructor m (Ref Edge edge)                    \
                   , Connectible (Ref Node node) (Ref Node node) m    \
                   , HasInputs (term ls) (Ref Edge edge)              \
                   , HasProp Type   node                              \
                   , HasProp TCData node                              \
                   , HasProp Succs  node                              \
                   , Prop Type   node ~ Ref Edge edge                 \
                   , Prop TCData node ~ TCDataPayload node            \
                   , Prop Succs  node ~ SizeTracking IntSet.IntSet    \
                   )

type NodeTranslator n = Ref Node n -> Ref Node n

mkNodeTranslator :: Map (Ref Node n) (Ref Node n) -> NodeTranslator n
mkNodeTranslator m r = case Map.lookup r m of
    Just res -> res
    Nothing  -> r

translateSignature :: NodeTranslator a -> Signature (Ref Node a) -> Signature (Ref Node a)
translateSignature f sig = sig & Function.self . mapped          %~ f
                               & Function.args . mapped . mapped %~ f
                               & Function.out                    %~ f

importStructure :: ImportCtx => [(Ref Node node, node)] -> [(Ref Edge edge, edge)] -> m (NodeTranslator node)
importStructure nodes' edges' = do
    let nodes           = filter ((/= universe) . fst) nodes'
        edges           = filter ((/= universe) . view target . snd) edges'
        foreignNodeRefs = fst <$> nodes
        foreignEdgeRefs = fst <$> edges

    newNodeRefs <- mapM (construct . (prop Succs .~ fromList []) . (prop TCData . belongsTo .~ [])) $ snd <$> nodes

    let nodeTrans         = Map.fromList $ zip foreignNodeRefs newNodeRefs
        translateNode     = mkNodeTranslator nodeTrans
        translateEdgeEnds = (source %~ translateNode) . (target %~ translateNode)
        foreignEs         = snd <$> edges
        es                = translateEdgeEnds <$> foreignEs

    newEdgeRefs <- forM es $ \e -> connection (e ^. source) (e ^. target)
    let edgeTrans = Map.fromList $ zip foreignEdgeRefs newEdgeRefs

    forM newNodeRefs $ \ref -> do
        node <- read ref
        let nodeWithFixedEdges = node & over covered     (fmapInputs unsafeTranslateEdge)
                                      & over (prop Type) unsafeTranslateEdge
                where
                unsafeTranslateEdge i = fromMaybe i $ Map.lookup i edgeTrans
        write ref nodeWithFixedEdges

    return translateNode

importToCluster :: ( ImportCtx
                   , clus ~ (NetClusterLayers :< SubGraph node)
                   , Covered clus
                   , Clusterable node clus m
                   , BiCastable clus c
                   ) => graph -> m (Ref Cluster clus, NodeTranslator node)
importToCluster g = do
    let foreignNodeRefs = Ref <$> usedIxes (g ^. wrapped . nodeGraph)
        foreignEdgeRefs = Ref <$> usedIxes (g ^. wrapped . edgeGraph)
        foreignNodes    = flip view g . focus <$> foreignNodeRefs
        foreignEdges    = flip view g . focus <$> foreignEdgeRefs
    trans <- importStructure (zip foreignNodeRefs foreignNodes) (zip foreignEdgeRefs foreignEdges)
    cls <- subgraph
    mapM (flip include cls) $ filter (/= universe) $ trans <$> foreignNodeRefs
    return (cls, trans)

dupCluster :: ( ImportCtx
              , Getter Inputs node
              , Prop Inputs node ~ [Ref Edge edge]
              , clus ~ (NetClusterLayers :< SubGraph node)
              , Covered clus
              , HasProp Name   clus
              , HasProp Lambda clus
              , Prop Name   clus ~ String
              , Prop Lambda clus ~ Maybe (Signature (Ref Node node))
              , Clusterable node clus m
              , BiCastable clus c
              ) => Ref Cluster clus -> String -> m (Ref Cluster clus, NodeTranslator node)
dupCluster cluster name = do
    nodeRefs <- members cluster
    nodes <- mapM read nodeRefs
    let gatherEdges n = foldr IntSet.insert mempty (view idx <$> ((n # Inputs) ++ [n ^. prop Type]))
    let edgeRefs = Ref <$> (IntSet.toList $ foldr IntSet.union mempty (gatherEdges <$> nodes))
    edges <- mapM read edgeRefs
    trans <- importStructure (zip nodeRefs nodes) (zip edgeRefs edges)
    fptr  <- follow (prop Lambda) cluster
    cl <- subgraph
    withRef cl $ (prop Name   .~ name)
               . (prop Lambda .~ (translateSignature trans <$> fptr))
    mapM (flip include cl) $ trans <$> nodeRefs
    return (cl, trans)

universe :: Ref Node n
universe = Ref 0
