Shader "WorldWonders/Stylized"
{
    // Aydınlık, stilize (yumuşak toon) aydınlatma. URP alt-gölgelendiricisi + yerleşik RP yedeği.
    Properties
    {
        _BaseColor("Base Color", Color) = (1,1,1,1)
        _ShadeColor("Shade Tint", Color) = (0.78,0.82,0.92,1)
        _RimColor("Rim Color", Color) = (1,1,1,1)
        _RimPower("Rim Power", Range(0.5,8)) = 3
        _RimStrength("Rim Strength", Range(0,1)) = 0.22
        _Emission("Emission", Range(0,1)) = 0
    }
    SubShader
    {
        Tags { "RenderType"="Opaque" "Queue"="Geometry" "RenderPipeline"="UniversalPipeline" }
        Pass
        {
            Name "ForwardLit"
            Tags { "LightMode"="UniversalForward" }
            Cull Back ZWrite On
            HLSLPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #pragma multi_compile_instancing
            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/Core.hlsl"
            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/Lighting.hlsl"
            CBUFFER_START(UnityPerMaterial)
                half4 _BaseColor; half4 _ShadeColor; half4 _RimColor; half _RimPower; half _RimStrength; half _Emission;
            CBUFFER_END
            struct Attributes { float4 positionOS : POSITION; float3 normalOS : NORMAL; UNITY_VERTEX_INPUT_INSTANCE_ID };
            struct Varyings { float4 positionCS : SV_POSITION; float3 normalWS : TEXCOORD0; float3 positionWS : TEXCOORD1; UNITY_VERTEX_INPUT_INSTANCE_ID };
            Varyings vert(Attributes i)
            {
                Varyings o;
                UNITY_SETUP_INSTANCE_ID(i);
                UNITY_TRANSFER_INSTANCE_ID(i, o);
                VertexPositionInputs p = GetVertexPositionInputs(i.positionOS.xyz);
                o.positionCS = p.positionCS;
                o.positionWS = p.positionWS;
                o.normalWS = TransformObjectToWorldNormal(i.normalOS);
                return o;
            }
            half4 frag(Varyings i) : SV_Target
            {
                UNITY_SETUP_INSTANCE_ID(i);
                float3 n = normalize(i.normalWS);
                Light l = GetMainLight();
                half ndl = saturate(dot(n, l.direction));
                half band = smoothstep(0.02, 0.55, ndl);
                half3 baseC = _BaseColor.rgb;
                half3 lit = lerp(_ShadeColor.rgb * baseC, baseC, band) * l.color;
                half3 amb = SampleSH(n) * baseC * 0.35;
                float3 v = normalize(GetWorldSpaceViewDir(i.positionWS));
                half rim = pow(1.0 - saturate(dot(n, v)), _RimPower) * _RimStrength;
                half3 c = lit + amb + rim * _RimColor.rgb + baseC * _Emission;
                return half4(c, _BaseColor.a);
            }
            ENDHLSL
        }
    }
    SubShader
    {
        Tags { "RenderType"="Opaque" "Queue"="Geometry" }
        Pass
        {
            Tags { "LightMode"="ForwardBase" }
            Cull Back ZWrite On
            CGPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #include "UnityCG.cginc"
            #include "UnityLightingCommon.cginc"
            fixed4 _BaseColor; fixed4 _ShadeColor; fixed4 _RimColor; half _RimPower; half _RimStrength; half _Emission;
            struct v2f { float4 pos : SV_POSITION; float3 n : TEXCOORD0; float3 wp : TEXCOORD1; };
            v2f vert(appdata_base v) { v2f o; o.pos = UnityObjectToClipPos(v.vertex); o.n = UnityObjectToWorldNormal(v.normal); o.wp = mul(unity_ObjectToWorld, v.vertex).xyz; return o; }
            fixed4 frag(v2f i) : SV_Target
            {
                float3 n = normalize(i.n);
                half ndl = saturate(dot(n, normalize(_WorldSpaceLightPos0.xyz)));
                half band = smoothstep(0.02, 0.55, ndl);
                half3 lit = lerp(_ShadeColor.rgb * _BaseColor.rgb, _BaseColor.rgb, band) * _LightColor0.rgb;
                half3 amb = ShadeSH9(half4(n, 1)) * _BaseColor.rgb * 0.35;
                half rim = pow(1.0 - saturate(dot(n, normalize(_WorldSpaceCameraPos - i.wp))), _RimPower) * _RimStrength;
                return fixed4(lit + amb + rim * _RimColor.rgb + _BaseColor.rgb * _Emission, _BaseColor.a);
            }
            ENDCG
        }
    }
}
