package sdk

import (
	"context"
	"net/url"
)

// ResolveName resolves a human-readable name to an on-chain address via
// the name service.
func (c *Client) ResolveName(ctx context.Context, name string) (*NameRecord, error) {
	raw, err := c.getAbsoluteJSON(ctx, c.baseURL+"/names/resolve/"+url.PathEscape(name))
	if err != nil {
		return nil, err
	}
	record := &NameRecord{}
	if v, ok := raw["name"].(string); ok {
		record.Name = v
	}
	if v, ok := raw["address"].(string); ok {
		record.Address = Address(v)
	}
	return record, nil
}

// ReverseResolveName resolves an on-chain address to a human-readable name.
func (c *Client) ReverseResolveName(ctx context.Context, address string) (*NameRecord, error) {
	raw, err := c.getAbsoluteJSON(ctx, c.baseURL+"/names/reverse/"+url.PathEscape(address))
	if err != nil {
		return nil, err
	}
	record := &NameRecord{}
	if v, ok := raw["name"].(string); ok {
		record.Name = v
	}
	if v, ok := raw["address"].(string); ok {
		record.Address = Address(v)
	}
	return record, nil
}
