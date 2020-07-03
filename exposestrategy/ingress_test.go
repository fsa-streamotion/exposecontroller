package exposestrategy

import (
	"fmt"
	"log"
	"testing"

	"github.com/stretchr/testify/assert"
	"k8s.io/kubernetes/pkg/api"
	"k8s.io/kubernetes/pkg/api/errors"
	"k8s.io/kubernetes/pkg/api/unversioned"
	"k8s.io/kubernetes/pkg/apis/extensions"
	"k8s.io/kubernetes/pkg/client/unversioned/testclient"
	"k8s.io/kubernetes/pkg/runtime"
	"k8s.io/kubernetes/pkg/types"
	"k8s.io/kubernetes/pkg/util/intstr"
)

var svcUID int = 1 << 24

func TestCreateIngress(t *testing.T) {
	examples := []struct {
		name     string
		strategy *IngressStrategy
		ingress  *extensions.Ingress
		service  *api.Service
		expected *extensions.Ingress
		hostname string
	}{{
		"no ingress",
		&IngressStrategy{
			domain:         "my-domain.com",
			internalDomain: "my-internal-domain.com",
			urltemplate:    "%s.%s.%s",
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace:   "my-namespace",
				Name:        "my-service",
				Annotations: map[string]string{"not-found": "true"},
			},
		},
		&api.Service{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
			},
			Spec: api.ServiceSpec{
				Ports: []api.ServicePort{
					{Port: 1234},
				},
			},
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/generated-by": "exposecontroller",
				},
				Labels: map[string]string{
					"provider": "fabric8",
				},
			},
			Spec: extensions.IngressSpec{
				Rules: []extensions.IngressRule{
					{
						Host: "my-service.my-namespace.my-domain.com",
						IngressRuleValue: extensions.IngressRuleValue{
							HTTP: &extensions.HTTPIngressRuleValue{
								Paths: []extensions.HTTPIngressPath{{
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(1234),
									},
									Path: "",
								}},
							},
						},
					},
				},
			},
		},
		"my-service.my-namespace.my-domain.com",
	}, {
		"no ingress, with aliasDomain",
		&IngressStrategy{
			domain:      "my-domain.com",
			aliasDomain: "my-alias-domain.com",
			urltemplate: "%s.%s.%s",
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace:   "my-namespace",
				Name:        "my-service",
				Annotations: map[string]string{"not-found": "true"},
			},
		},
		&api.Service{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
			},
			Spec: api.ServiceSpec{
				Ports: []api.ServicePort{
					{Port: 1234},
				},
			},
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/generated-by":                  "exposecontroller",
					"nginx.ingress.kubernetes.io/server-alias": "my-service.my-namespace.my-domain.com, my-service.my-namespace.my-alias-domain.com",
				},
				Labels: map[string]string{
					"provider": "fabric8",
				},
			},
			Spec: extensions.IngressSpec{
				Rules: []extensions.IngressRule{
					{
						Host: "my-service.my-namespace.my-domain.com",
						IngressRuleValue: extensions.IngressRuleValue{
							HTTP: &extensions.HTTPIngressRuleValue{
								Paths: []extensions.HTTPIngressPath{{
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(1234),
									},
									Path: "",
								}},
							},
						},
					},
				},
			},
		},
		"my-service.my-namespace.my-domain.com",
	}, {
		"ingress with aliasDomain, aliasDomain needs update",
		&IngressStrategy{
			domain:      "my-domain.com",
			aliasDomain: "my-new-alias-domain.com",
			urltemplate: "%s.%s.%s",
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/generated-by":                  "exposecontroller",
					"nginx.ingress.kubernetes.io/server-alias": "my-service.my-namespace.my-domain.com, my-service.my-namespace.my-old-alias-domain.com",
				},
			},
		},
		&api.Service{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
			},
			Spec: api.ServiceSpec{
				Ports: []api.ServicePort{
					{Port: 1234},
				},
			},
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/generated-by":                  "exposecontroller",
					"nginx.ingress.kubernetes.io/server-alias": "my-service.my-namespace.my-domain.com, my-service.my-namespace.my-new-alias-domain.com",
				},
				Labels: map[string]string{
					"provider": "fabric8",
				},
			},
			Spec: extensions.IngressSpec{
				Rules: []extensions.IngressRule{
					{
						Host: "my-service.my-namespace.my-domain.com",
						IngressRuleValue: extensions.IngressRuleValue{
							HTTP: &extensions.HTTPIngressRuleValue{
								Paths: []extensions.HTTPIngressPath{{
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(1234),
									},
									Path: "",
								}},
							},
						},
					},
				},
			},
		},
		"my-service.my-namespace.my-domain.com",
	}, {
		"no ingress, service annotations",
		&IngressStrategy{
			domain:         "my-domain.com",
			internalDomain: "my-internal-domain.com",
			urltemplate:    "%s-%s.%s",
			tlsSecretName:  "my-secret",
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace:   "my-namespace",
				Name:        "my-ingress",
				Annotations: map[string]string{"not-found": "true"},
			},
		},
		&api.Service{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/ingress.name":        "my-ingress",
					"fabric8.io/host.name":           "my-hostname",
					"fabric8.io/ingress.path":        "/my-path",
					"fabric8.io/ingress.annotations": "annotation-1: value-1\nannotation-2: value-2",
				},
			},
			Spec: api.ServiceSpec{
				Ports: []api.ServicePort{
					{Port: 1234},
					{Port: 5678},
				},
			},
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-ingress",
				Annotations: map[string]string{
					"fabric8.io/generated-by": "exposecontroller",
					"annotation-1":            "value-1",
					"annotation-2":            "value-2",
				},
				Labels: map[string]string{
					"provider": "fabric8",
				},
			},
			Spec: extensions.IngressSpec{
				Rules: []extensions.IngressRule{
					{
						Host: "my-hostname-my-namespace.my-domain.com",
						IngressRuleValue: extensions.IngressRuleValue{
							HTTP: &extensions.HTTPIngressRuleValue{
								Paths: []extensions.HTTPIngressPath{{
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(1234),
									},
									Path: "/my-path",
								}},
							},
						},
					},
				},
				TLS: []extensions.IngressTLS{
					{
						Hosts:      []string{"my-hostname-my-namespace.my-domain.com"},
						SecretName: "my-secret",
					},
				},
			},
		},
		"my-hostname-my-namespace.my-domain.com",
	}, {
		"ingress up to date",
		&IngressStrategy{
			domain:         "my-domain.com",
			internalDomain: "my-internal-domain.com",
			urltemplate:    "%s.%s.%s",
			tlsAcme:        true,
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/generated-by": "exposecontroller",
					"kubernetes.io/tls-acme":  "true",
				},
				Labels: map[string]string{
					"provider": "fabric8",
				},
			},
			Spec: extensions.IngressSpec{
				Rules: []extensions.IngressRule{
					{
						Host: "my-service.my-namespace.my-domain.com",
						IngressRuleValue: extensions.IngressRuleValue{
							HTTP: &extensions.HTTPIngressRuleValue{
								Paths: []extensions.HTTPIngressPath{{
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(3456),
									},
									Path: "",
								}},
							},
						},
					},
				},
				TLS: []extensions.IngressTLS{
					{
						Hosts:      []string{"my-service.my-namespace.my-domain.com"},
						SecretName: "tls-my-service",
					},
				},
			},
		},
		&api.Service{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					ExposePortAnnotationKey: "3456",
				},
			},
			Spec: api.ServiceSpec{
				Ports: []api.ServicePort{
					{Port: 1234},
					{Port: 3456},
				},
			},
		},
		nil,
		"my-service.my-namespace.my-domain.com",
	}, {
		"ingress needs update",
		&IngressStrategy{
			domain:         "my-domain.com",
			internalDomain: "my-internal-domain.com",
			urltemplate:    "%s.%s.%s",
			tlsAcme:        true,
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/generated-by": "exposecontroller",
					"kubernetes.io/tls-acme":  "true",
				},
				Labels: map[string]string{
					"provider": "fabric8",
				},
			},
			Spec: extensions.IngressSpec{
				Rules: []extensions.IngressRule{
					{
						Host: "my-service.my-namespace.my-domain.com",
						IngressRuleValue: extensions.IngressRuleValue{
							HTTP: &extensions.HTTPIngressRuleValue{
								Paths: []extensions.HTTPIngressPath{{
									Backend: extensions.IngressBackend{
										ServiceName: "other-service",
										ServicePort: intstr.FromInt(6789),
									},
									Path: "/other",
								}},
							},
						},
					},
				},
				TLS: []extensions.IngressTLS{
					{
						Hosts:      []string{"my-service.my-namespace.my-domain.com"},
						SecretName: "tls-my-service",
					},
				},
			},
		},
		&api.Service{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					ExposePortAnnotationKey: "3456",
				},
			},
			Spec: api.ServiceSpec{
				Ports: []api.ServicePort{
					{Port: 1234},
					{Port: 3456},
				},
			},
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/generated-by": "exposecontroller",
					"kubernetes.io/tls-acme":  "true",
				},
				Labels: map[string]string{
					"provider": "fabric8",
				},
			},
			Spec: extensions.IngressSpec{
				Rules: []extensions.IngressRule{
					{
						Host: "my-service.my-namespace.my-domain.com",
						IngressRuleValue: extensions.IngressRuleValue{
							HTTP: &extensions.HTTPIngressRuleValue{
								Paths: []extensions.HTTPIngressPath{{
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(3456),
									},
									Path: "",
								}},
							},
						},
					},
				},
				TLS: []extensions.IngressTLS{
					{
						Hosts:      []string{"my-service.my-namespace.my-domain.com"},
						SecretName: "tls-my-service",
					},
				},
			},
		},
		"my-service.my-namespace.my-domain.com",
	}, {
		"ingress keep extra rules",
		&IngressStrategy{
			domain:         "my-domain.com",
			internalDomain: "my-internal-domain.com",
			urltemplate:    "%s.%s.%s",
			tlsAcme:        true,
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/generated-by": "exposecontroller",
				},
				Labels: map[string]string{
					"provider": "fabric8",
				},
			},
			Spec: extensions.IngressSpec{
				Rules: []extensions.IngressRule{
					{
						Host: "my-service.my-namespace.my-domain.com",
						IngressRuleValue: extensions.IngressRuleValue{
							HTTP: &extensions.HTTPIngressRuleValue{
								Paths: []extensions.HTTPIngressPath{{
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(3456),
									},
									Path: "",
								}, {
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(3456),
									},
									Path: "",
								}},
							},
						},
					},
				},
				TLS: []extensions.IngressTLS{
					{
						Hosts:      []string{"my-service.my-namespace.my-domain.com"},
						SecretName: "tls-my-service",
					},
				},
			},
		},
		&api.Service{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					ExposePortAnnotationKey: "3456",
				},
			},
			Spec: api.ServiceSpec{
				Ports: []api.ServicePort{
					{Port: 1234},
					{Port: 3456},
				},
			},
		},
		&extensions.Ingress{
			ObjectMeta: api.ObjectMeta{
				Namespace: "my-namespace",
				Name:      "my-service",
				Annotations: map[string]string{
					"fabric8.io/generated-by": "exposecontroller",
					"kubernetes.io/tls-acme":  "true",
				},
				Labels: map[string]string{
					"provider": "fabric8",
				},
			},
			Spec: extensions.IngressSpec{
				Rules: []extensions.IngressRule{
					{
						Host: "my-service.my-namespace.my-domain.com",
						IngressRuleValue: extensions.IngressRuleValue{
							HTTP: &extensions.HTTPIngressRuleValue{
								Paths: []extensions.HTTPIngressPath{{
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(3456),
									},
									Path: "",
								}, {
									Backend: extensions.IngressBackend{
										ServiceName: "my-service",
										ServicePort: intstr.FromInt(3456),
									},
									Path: "",
								}},
							},
						},
					},
				},
				TLS: []extensions.IngressTLS{
					{
						Hosts:      []string{"my-service.my-namespace.my-domain.com"},
						SecretName: "tls-my-service",
					},
				},
			},
		},
		"my-service.my-namespace.my-domain.com",
	}}
	for _, example := range examples {
		svcUID++
		example.service.UID = types.UID(fmt.Sprintf("%d", svcUID))
		if example.ingress.Annotations["not-found"] != "true" {
			svcUID++
			example.ingress.UID = types.UID(fmt.Sprintf("%d", svcUID))
			if example.ingress.OwnerReferences == nil {
				example.ingress.OwnerReferences = []api.OwnerReference{
					{
						APIVersion: "v1",
						Kind:       "Service",
						Name:       example.service.Name,
						UID:        example.service.UID,
					},
				}
			}
		}
		if example.expected != nil {
			example.expected.UID = example.ingress.UID
			example.expected.OwnerReferences = []api.OwnerReference{
				{
					APIVersion: "v1",
					Kind:       "Service",
					Name:       example.service.Name,
					UID:        example.service.UID,
				},
			}
		}

		updated := false
		client := &testclient.FakeExperimental{&testclient.Fake{}}
		client.Fake.PrependReactor("*", "*", func(action testclient.Action) (bool, runtime.Object, error) {
			gr := unversioned.GroupResource{
				Group:    "v1beta1",
				Resource: action.GetResource(),
			}
			log.Printf("example \"%s\" action %v", example.name, action)
			switch action.GetVerb() {
			case "get":
				castAction := action.(testclient.GetAction)
				if assert.Equal(t, "ingresses", castAction.GetResource(), example.name) &&
					assert.Equal(t, example.ingress.Namespace, castAction.GetNamespace(), example.name) &&
					assert.Equal(t, example.ingress.Name, castAction.GetName(), example.name) {
					if example.ingress.Annotations["not-found"] == "true" {
						return true, nil, errors.NewNotFound(gr, example.ingress.Name)
					}
					ingress, err := api.Scheme.DeepCopy(example.ingress)
					if !assert.NoError(t, err, example.name) {
						return true, nil, errors.NewInternalError(err)
					}
					return true, ingress.(*extensions.Ingress), nil
				}
			case "update":
				castAction := action.(testclient.UpdateAction)
				if assert.Equal(t, "ingresses", castAction.GetResource(), example.name) &&
					assert.Equal(t, example.ingress.Namespace, castAction.GetNamespace(), example.name) &&
					assert.IsType(t, &extensions.Ingress{}, castAction.GetObject(), example.name) &&
					assert.NotNil(t, example.expected, example.name) &&
					assert.False(t, updated, example.name) {
					updated = true
					ingress := castAction.GetObject().(*extensions.Ingress)
					if !assert.NotNil(t, example.expected, example.name) ||
						!assert.False(t, example.ingress.Annotations["not-found"] == "true", example.name) ||
						!assert.Equal(t, example.ingress.UID, ingress.UID, example.name) {
						return true, nil, errors.NewConflict(gr, example.ingress.Name, fmt.Errorf("unexpected update"))
					}
					assert.Equal(t, example.expected, ingress, example.name)
					return true, ingress, nil
				}
			case "create":
				castAction := action.(testclient.CreateAction)
				if assert.Equal(t, "ingresses", castAction.GetResource(), example.name) &&
					assert.Equal(t, example.ingress.Namespace, castAction.GetNamespace(), example.name) &&
					assert.IsType(t, &extensions.Ingress{}, castAction.GetObject(), example.name) &&
					assert.NotNil(t, example.expected, example.name) &&
					assert.False(t, updated, example.name) {
					updated = true
					ingress := castAction.GetObject().(*extensions.Ingress)
					if !assert.NotNil(t, example.expected, example.name) ||
						!assert.True(t, example.ingress.Annotations["not-found"] == "true", example.name) ||
						!assert.Equal(t, types.UID(""), ingress.UID, example.name) {
						return true, nil, errors.NewConflict(gr, example.ingress.Name, fmt.Errorf("unexpected update"))
					}
					assert.Equal(t, example.expected, ingress, example.name)
					return true, ingress, nil
				}
			}
			assert.Fail(t, fmt.Sprintf("%s: unexpected action %v", example.name, action))
			return true, nil, errors.NewBadRequest("unexpected action")
		})
		hostname, err := example.strategy.createIngress(client, example.service)
		if assert.NoError(t, err, example.name) {
			assert.Equal(t, example.hostname, hostname, example.name)
			if example.expected != nil {
				assert.True(t, updated, example.name)
			}
		}
	}
}
